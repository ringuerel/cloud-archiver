package com.homelab.ringue.cloud.archiver.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.BackupPipelineContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.FolderBackupService;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Scope("prototype")
public class FolderBackupServiceImpl implements FolderBackupService {

    private static final String MDC_SCAN_FOLDER = "scanFolder";
    private static final String MDC_FILE_PATH = "filePath";

    private final FileCatalogItemRepository fileCatalogItemRepository;
    private final FileCatalogItemMapper fileCatalogItemMapper;
    private final CloudProviderFactory cloudProviderFactory;
    private final ApplicationProperties applicationProperties;
    private final NotificationService notificationService;
    private final ThumbnailService thumbnailService;
    private final Counter filesUploadedCounter;
    private final Timer uploadTimer;
    private final DistributionSummary gcpUploadBytesSummary;

    public FolderBackupServiceImpl(
            FileCatalogItemRepository fileCatalogItemRepository,
            FileCatalogItemMapper fileCatalogItemMapper,
            CloudProviderFactory cloudProviderFactory,
            ApplicationProperties applicationProperties,
            NotificationService notificationService,
            ThumbnailService thumbnailService,
            MeterRegistry meterRegistry) {
        this.fileCatalogItemRepository = fileCatalogItemRepository;
        this.fileCatalogItemMapper = fileCatalogItemMapper;
        this.cloudProviderFactory = cloudProviderFactory;
        this.applicationProperties = applicationProperties;
        this.notificationService = notificationService;
        this.thumbnailService = thumbnailService;
        this.filesUploadedCounter = Counter.builder("cloud_archiver_files_uploaded_total")
                .description("Total number of files successfully uploaded to the cloud")
                .register(meterRegistry);
        this.uploadTimer = Timer.builder("cloud_archiver_upload_duration_seconds")
                .description("Time taken for file upload operations")
                .register(meterRegistry);
        this.gcpUploadBytesSummary = DistributionSummary.builder("cloud_archiver_gcp_upload_bytes")
                .description("Total bytes uploaded to GCP")
                .baseUnit("bytes")
                .register(meterRegistry);
    }

    @Override
    public BackupPipelineContext backUpFolder(ScanLocationConfig locationConfig) throws CloudBackupException {
        log.trace("Starting on backup:{}", locationConfig.getScanFolder());
        Path folder = Paths.get(locationConfig.getScanFolder());
        log.trace("Got path for folder:{}", folder);
        Map<String, FileCatalogItem> collectionIdsInMemoryCache = new ConcurrentHashMap<>();
        log.trace("About to get documents from collection:{}", folder);
        putCollectionIdsInMemoryCache(locationConfig, collectionIdsInMemoryCache);
        log.trace("InMemory collection size:{}", collectionIdsInMemoryCache.size());

        BackupPipelineContext context = new BackupPipelineContext(
                collectionIdsInMemoryCache,
                new AtomicInteger(0),
                new AtomicLong(0));

        try (Stream<Path> filesList = Files.walk(folder).parallel()) {
            processFileStreamForBackup(locationConfig, context, filesList);
        } catch (Exception e) {
            log.error("Failed processing {} for cloud backup", locationConfig, e);
            notificationService.notifyError("Error during cloud backup:" + e.getMessage(), locationConfig);
            throw new CloudBackupException(locationConfig.getScanFolder(), e);
        }

        log.debug("Finished with backup process: {}", locationConfig.getScanFolder());
        return context;
    }

    @Override
    public void processFileStreamForBackup(ScanLocationConfig locationConfig, BackupPipelineContext context,
            Stream<Path> filesStream) {
        locationConfig.initScanConfigLocation();
        filesStream
                .map(fileCatalogItemMapper::mapFromPath)
                .filter(Objects::nonNull)
                .filter(fileCatalogItem -> this.applyFilteringRules(locationConfig, fileCatalogItem))
                .filter(fileCatalogItem -> !fileCatalogItem.isDirectory())
                .map(fileOnDisk -> getFileToProcessIfAny(context.catalogCache(), fileOnDisk))
                .filter(Objects::nonNull)
                .forEach(fileCatalogItem -> performCloudBackup(locationConfig, context, fileCatalogItem));

        log.info("Updating metadata only for {} items on {}", context.catalogCache().size(), locationConfig.getScanFolder());
        context.catalogCache().values().forEach(fileCatalogItemRepository::save);
    }

    FileCatalogItem getFileToProcessIfAny(Map<String, FileCatalogItem> collectionIdsInMemoryCache,
            FileCatalogItem fileOnDisk) {
        FileCatalogItem existingItem = collectionIdsInMemoryCache.remove(fileOnDisk.absolutePath());
        if (existingItem != null
                && existingItem.lastModified() != null
                && existingItem.lastModified().toEpochMilli() == fileOnDisk.lastModified().toEpochMilli()) {
            log.debug("Skipping upload decision because lastModified is unchanged for {}", fileOnDisk.absolutePath());
            return null;
        }

        FileCatalogItem crc32cPopulatedItem = getCrC32CPopulatedItem(fileOnDisk);
        if (crc32cPopulatedItem == null) {
            log.warn("Skipping upload decision because CRC32C generation failed for {}", fileOnDisk.absolutePath());
            return null;
        }

        if (existingItem != null && crc32cPopulatedItem.crc32c().equals(existingItem.crc32c())) {
            log.debug("Skipping upload because CRC32C is unchanged for {}", fileOnDisk.absolutePath());
            collectionIdsInMemoryCache.put(
                    crc32cPopulatedItem.absolutePath(),
                    fileCatalogItemMapper.mapFromFileCatalogItemUpdateLastModified(existingItem, crc32cPopulatedItem.lastModified()));
            return null;
        }

        log.info("Scheduling upload for {} (newFile={}, checksumChanged={})",
                fileOnDisk.absolutePath(),
                existingItem == null,
                existingItem != null);
        return crc32cPopulatedItem;
    }

    private void putCollectionIdsInMemoryCache(ScanLocationConfig locationConfig,
            Map<String, FileCatalogItem> catalogItemsKeys) {
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        int pageSize = locationConfig.getCollectionFetchSize();
        int pageNumber = 0;
        var pageRequest = org.springframework.data.domain.PageRequest.of(pageNumber, pageSize);
        var archivedCatalogItems = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder, pageRequest);
        while (true) {
            archivedCatalogItems.getContent().stream()
                    .forEach(fileCatalogItem -> catalogItemsKeys.put(fileCatalogItem.absolutePath(), fileCatalogItem));
            if (!archivedCatalogItems.hasNext()) {
                return;
            }
            archivedCatalogItems = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder,
                    archivedCatalogItems.nextPageable());
        }
    }

    protected boolean applyFilteringRules(ScanLocationConfig locationConfig, FileCatalogItem fileCatalogItem) {
        boolean ignoreHiddenFile = locationConfig.isIgnoreHiddenFiles();
        if (ignoreHiddenFile) {
            try {
                ignoreHiddenFile = Files.isHidden(Paths.get(fileCatalogItem.absolutePath()));
            } catch (IOException e) {
                log.warn("Failed to resolve file visibility, defaults to hidden {}", fileCatalogItem.absolutePath());
            }
        }
        if (ignoreHiddenFile) {
            log.trace("Ignored by hidden file {}", fileCatalogItem.absolutePath());
            return false;
        }
        boolean shallIgnoreByPattern = locationConfig.getCompiledIgnorePatterns().stream()
                .anyMatch(pattern -> pattern.matcher(fileCatalogItem.fileName()).matches());
        if (shallIgnoreByPattern) {
            log.trace("Ignored by configured pattern: {}", fileCatalogItem.absolutePath());
            return false;
        }
        return true;
    }

    void performCloudBackup(ScanLocationConfig locationConfig, BackupPipelineContext context, FileCatalogItem fileCatalogItem) {
        withBackupMdc(locationConfig, fileCatalogItem, () -> {
            try {
                log.debug("[GCP] Performing cloud backup for: {} with a size of: {}",
                        fileCatalogItem.absolutePath(), fileCatalogItem.fileSize());
                FileCatalogItem archivableItem = fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(fileCatalogItem);
                Instant uploadStart = Instant.now();
                long startTime = System.nanoTime();
                cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType())
                        .upload(archivableItem);
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;

                gcpUploadBytesSummary.record(archivableItem.fileSize());

                log.info("[GCP] Uploaded {} ({} bytes) in {} ms",
                        archivableItem.absolutePath(), archivableItem.fileSize(), durationMs);
                FileCatalogItem itemWithThumbnail = thumbnailService.createOrUpdateThumbnail(archivableItem, false);
                fileCatalogItemRepository.save(itemWithThumbnail);
                context.uploadedCount().incrementAndGet();
                context.uploadedSize().addAndGet(archivableItem.fileSize());
                filesUploadedCounter.increment();
                uploadTimer.record(Duration.between(uploadStart, Instant.now()));
            } catch (Exception e) {
                log.error("[GCP] Failed to upload {} to the cloud provider for scanFolder {}",
                        fileCatalogItem.absolutePath(), locationConfig.getScanFolder(), e);
                notificationService.notifyError(
                        "Failed to upload " + fileCatalogItem.absolutePath() + ": " + e.getMessage(),
                        locationConfig);
            }
        });
    }

    @Override
    public String getCrC32C(String absolutePath) throws IOException {
        byte[] crc32CheckSum = new byte[0];
        File file = Paths.get(absolutePath).toFile();
        if (!file.exists()) {
            throw new NoSuchFileException(absolutePath);
        }
        byte[] buffer = new byte[applicationProperties.getCrc32cBufferSize()];
        int limit = -1;
        HashFunction crc32cHashFunc = Hashing.crc32c();
        Hasher crc32cHasher = crc32cHashFunc.newHasher();
        try (FileInputStream fis = new FileInputStream(file)) {
            while ((limit = fis.read(buffer)) > 0) {
                crc32cHasher.putBytes(buffer, 0, limit);
            }
            crc32CheckSum = Ints.toByteArray(crc32cHasher.hash().asInt());
        } catch (IOException e) {
            log.error("Unable to get crc32c for {}", absolutePath, e);
        }
        return BaseEncoding.base64().encode(crc32CheckSum);
    }

    protected FileCatalogItem getCrC32CPopulatedItem(FileCatalogItem fileCatalogItem) {
        try {
            return fileCatalogItemMapper.mapFromFileCatalogItemUpdateCheckSum(
                    fileCatalogItem,
                    getCrC32C(fileCatalogItem.absolutePath()));
        } catch (IOException e) {
            log.error("{} failed on crc32c generation", fileCatalogItem.absolutePath(), e);
            return null;
        }
    }

    private String fixLocationPath(String locationPath) {
        CharSequence charSequence = new SimpleMessage("\\\\");
        return locationPath.replace(charSequence, "\\");
    }

    private void withBackupMdc(ScanLocationConfig locationConfig, FileCatalogItem fileCatalogItem, Runnable action) {
        try (CloudSyncContext.Scope ignored = CloudSyncContext.preserve()) {
            CloudSyncContext.put(MDC_SCAN_FOLDER, locationConfig.getScanFolder());
            CloudSyncContext.put(MDC_FILE_PATH, fileCatalogItem.absolutePath());
            CloudSyncContext.put("backupDecisionId",
                    UUID.nameUUIDFromBytes(fileCatalogItem.absolutePath().getBytes(StandardCharsets.UTF_8)).toString());
            action.run();
        }
    }
}
