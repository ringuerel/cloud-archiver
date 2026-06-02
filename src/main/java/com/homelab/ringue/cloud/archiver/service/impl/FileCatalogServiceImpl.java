package com.homelab.ringue.cloud.archiver.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.PendingDeletionItem;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.repository.SyncSummaryRepository;
import com.homelab.ringue.cloud.archiver.service.BackupPipelineContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetricsService;
import com.homelab.ringue.cloud.archiver.service.CloudSyncOrchestrator;
import com.homelab.ringue.cloud.archiver.service.FileCatalogService;
import com.homelab.ringue.cloud.archiver.service.FolderBackupService;
import com.homelab.ringue.cloud.archiver.service.LocationSyncOperations;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Scope("prototype")
public class FileCatalogServiceImpl implements FileCatalogService, LocationSyncOperations {

    private static final String PHASE_DOWNLOAD = "download";
    private static final String PHASE_BACKUP = "backup";
    private static final String PHASE_CLEANUP = "cleanup";
    private static final String PHASE_SUMMARY = "summary";

    private final FileCatalogItemRepository fileCatalogItemRepository;
    private final SyncSummaryRepository syncSummaryRepository;
    private final CloudProviderFactory cloudProviderFactory;
    private final ApplicationProperties applicationProperties;
    private final NotificationService notificationService;
    private final CloudSyncMetricsService cloudSyncMetricsService;
    private final CloudSyncOrchestrator cloudSyncOrchestrator;
    private final FolderBackupService folderBackupService;
    private final ThumbnailService thumbnailService;

    private final AtomicInteger catalogCount = new AtomicInteger(0);
    private final AtomicLong catalogSize = new AtomicLong(0);

    @Autowired
    public FileCatalogServiceImpl(FileCatalogItemRepository fileCatalogItemRepository,
            CloudProviderFactory cloudProviderFactory,
            ApplicationProperties applicationProperties,
            SyncSummaryRepository syncSummaryRepository,
            NotificationService notificationService,
            CloudSyncMetricsService cloudSyncMetricsService,
            CloudSyncOrchestrator cloudSyncOrchestrator,
            FolderBackupService folderBackupService,
            ThumbnailService thumbnailService) {
        this.fileCatalogItemRepository = fileCatalogItemRepository;
        this.cloudProviderFactory = cloudProviderFactory;
        this.applicationProperties = applicationProperties;
        this.syncSummaryRepository = syncSummaryRepository;
        this.notificationService = notificationService;
        this.cloudSyncMetricsService = cloudSyncMetricsService;
        this.cloudSyncOrchestrator = cloudSyncOrchestrator;
        this.folderBackupService = folderBackupService;
        this.thumbnailService = thumbnailService;
    }

    @Override
    public boolean downloadFromCloud(String cloudPath) {
        try (CloudSyncContext.Scope ignored = CloudSyncContext.open(CloudSyncContext.newRunId(), null, PHASE_DOWNLOAD)) {
            String downloadRoot = applicationProperties.getDownloadRoot();
            if (downloadRoot == null || downloadRoot.isEmpty()) {
                log.error("downloadRoot is not configured");
                return false;
            }
            var cloudProvider = cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType());
            Path localTargetPathObj = Paths.get(downloadRoot, cloudPath);

            log.info("Starting cloud download for {} into {}", cloudPath, localTargetPathObj);
            long startTime = System.nanoTime();
            cloudProvider.download(cloudPath, downloadRoot);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            long downloadedSize = calculateDownloadedSize(localTargetPathObj);
            cloudSyncMetricsService.current().gcpDownloadsCounter().increment();
            cloudSyncMetricsService.current().gcpDownloadBytesSummary().record(downloadedSize);

            log.info("Completed cloud download for {} ({} bytes) in {} ms", cloudPath, downloadedSize, durationMs);
            return true;
        } catch (Exception e) {
            log.error("Failed to download {} from cloud provider", cloudPath, e);
            return false;
        } finally {
            CloudSyncContext.clear();
        }
    }

    private long calculateDownloadedSize(Path localTargetPathObj) {
        long downloadedSize = 0;
        try {
            if (Files.isRegularFile(localTargetPathObj)) {
                downloadedSize = Files.size(localTargetPathObj);
            } else if (Files.isDirectory(localTargetPathObj)) {
                try (Stream<Path> walk = Files.walk(localTargetPathObj)) {
                    downloadedSize = walk.filter(Files::isRegularFile)
                            .mapToLong(this::safeFileSize)
                            .sum();
                }
            }
        } catch (IOException e) {
            log.warn("Could not determine downloaded file size for {}", localTargetPathObj, e);
        }
        return downloadedSize;
    }

    private long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.warn("Could not determine size for file {}: {}", path, e.getMessage());
            return 0L;
        }
    }

    @Override
    public List<FileCatalogItem> findByFileNameContains(String fileName) {
        return fileCatalogItemRepository.findByFileNameContains(fileName);
    }

    @Override
    public List<FileCatalogItem> findByFileNameSimilar(String fileName) {
        return fileCatalogItemRepository.findByFileNameContainsIgnoreCase(fileName);
    }

    @Override
    public List<FileCatalogItem> findByArchiveDateBetweenAndAbsolutePathStartsWith(String startDate, String endDate,
            Optional<String> path) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date start = formatter.parse(startDate);
            Date end = formatter.parse(endDate);
            if (path.isPresent()) {
                return fileCatalogItemRepository.findByArchiveDateBetweenAndAbsolutePathStartsWith(start, end,
                        path.get());
            }
            return fileCatalogItemRepository.findByArchiveDateBetween(start, end);
        } catch (ParseException e) {
            log.error("Failed to parse date for archived items search", e);
            throw new IllegalArgumentException("Invalid date format. Please use yyyy-MM-dd.");
        }
    }

    @Override
    public List<PendingDeletionItem> findPendingDeletion(Optional<String> fileNameContains, Optional<String> fileNameExact,
            Optional<String> path) {
        List<ScanLocationConfig> scanFolders = Optional.ofNullable(applicationProperties.getScanFolders())
                .orElse(List.of());

        int fetchSize = path.map(p -> resolveOwningLocation(p, scanFolders))
                .map(ScanLocationConfig::getCollectionFetchSize)
                .orElse(500);

        String fixedPath = path.map(this::fixLocationPath).orElse(null);

        List<FileCatalogItem> allItems = new ArrayList<>();
        Pageable pageRequest = PageRequest.ofSize(fetchSize);
        Page<FileCatalogItem> page;

        do {
            page = queryForPendingDeletion(fileNameContains, fileNameExact, fixedPath, pageRequest);
            allItems.addAll(page.getContent());
            pageRequest = page.nextPageable();
        } while (page.hasNext());

        return allItems.stream()
                .filter(item -> Files.notExists(Paths.get(item.absolutePath())))
                .map(item -> toPendingDeletionItem(item, scanFolders))
                .toList();
    }

    private Page<FileCatalogItem> queryForPendingDeletion(Optional<String> fileNameContains,
            Optional<String> fileNameExact,
            String fixedPath,
            Pageable pageRequest) {

        if (fileNameContains.isPresent() && fixedPath != null) {
            return fileCatalogItemRepository.findByFileNameContainsIgnoreCaseAndParentFolderStartsWith(
                    fileNameContains.get(), fixedPath, pageRequest);
        }
        if (fileNameContains.isPresent()) {
            return fileCatalogItemRepository.findByFileNameContainsIgnoreCase(fileNameContains.get(), pageRequest);
        }
        if (fileNameExact.isPresent() && fixedPath != null) {
            return fileCatalogItemRepository.findByFileNameAndParentFolderStartsWith(
                    fileNameExact.get(), fixedPath, pageRequest);
        }
        if (fileNameExact.isPresent()) {
            return fileCatalogItemRepository.findByFileName(fileNameExact.get(), pageRequest);
        }
        if (fixedPath != null) {
            return fileCatalogItemRepository.findByParentFolderStartsWith(fixedPath, pageRequest);
        }
        return fileCatalogItemRepository.findAll(pageRequest);
    }

    private PendingDeletionItem toPendingDeletionItem(FileCatalogItem item, List<ScanLocationConfig> scanFolders) {
        ScanLocationConfig location = resolveOwningLocation(item.absolutePath(), scanFolders);
        if (location == null) {
            return new PendingDeletionItem(item, 0L, "unknown");
        }
        long days = computeDaysUntilDeletion(item, location);
        return new PendingDeletionItem(item, days, location.getScanFolder());
    }

    ScanLocationConfig resolveOwningLocation(String absolutePath, List<ScanLocationConfig> scanFolders) {
        String normalizedAbsolutePath = absolutePath.replace('\\', '/');
        return scanFolders.stream()
                .filter(loc -> loc.getScanFolder() != null)
                .filter(loc -> normalizedAbsolutePath.startsWith(loc.getScanFolder().replace('\\', '/')))
                .max(Comparator.comparingInt(loc -> loc.getScanFolder().length()))
                .orElse(null);
    }

    long computeDaysUntilDeletion(FileCatalogItem item, ScanLocationConfig location) {
        if (item.archiveDate() == null) {
            return 0L;
        }
        Integer standardLimit = location.getStandardDeleteDaysLimit();
        if (standardLimit == null) {
            return 0L;
        }
        int holdDays = Optional.ofNullable(location.getArchiveDeleteDaysHold()).orElse(0);
        LocalDate archiveLocalDate = item.archiveDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate eligibleDate = archiveLocalDate.plusDays((long) standardLimit + holdDays);
        return ChronoUnit.DAYS.between(LocalDate.now(), eligibleDate);
    }

    @Override
    public void performLocationSync(ScanLocationConfig scanlocationconfig) throws CloudBackupException {
        cloudSyncOrchestrator.performLocationSync(scanlocationconfig);
    }

    @Override
    public boolean startAllLocationSyncs() {
        cloudSyncMetricsService.reset();
        return cloudSyncOrchestrator.startAllLocationSyncs();
    }

    @Override
    public SyncSummaryItem executeBackup(ScanLocationConfig locationConfig) throws CloudBackupException {
        notificationService.notifyInfoMessage("Started backup process", locationConfig);
        startCloudBackup(locationConfig);
        return new SyncSummaryItem(null, catalogCount.get(), catalogSize.get(), 0, 0L, Instant.now());
    }

    @Override
    public SyncSummaryItem executeCleanup(ScanLocationConfig locationConfig) throws CloudBackupException {
        startCloudCleanup(locationConfig);
        return new SyncSummaryItem(null, 0, 0L, catalogCount.get(), catalogSize.get(), Instant.now());
    }

    @Override
    public void persistSummary(SyncSummaryItem summaryItem, ScanLocationConfig locationConfig) {
        addSummaryEntry(
                summaryItem.uploadCount(),
                summaryItem.uploadSize(),
                summaryItem.deleteCount(),
                summaryItem.deleteSize(),
                locationConfig);
    }

    private void startCloudBackup(ScanLocationConfig locationConfig) throws CloudBackupException {
        CloudSyncContext.updatePhase(PHASE_BACKUP);
        log.info("Starting cloud backup with provider {}", applicationProperties.getCloudProviderConfig().getType());
        BackupPipelineContext backupPipelineContext = folderBackupService.backUpFolder(locationConfig);
        catalogCount.set(backupPipelineContext.uploadedCount().get());
        catalogSize.set(backupPipelineContext.uploadedSize().get());
        log.info("Completed cloud backup with {} items and {} bytes uploaded", catalogCount.get(), catalogSize.get());
    }

    private void addSummaryEntry(int uploadCount, long uploadSize, int deleteCount, long deleteSize,
            ScanLocationConfig scanlocationconfig) {
        CloudSyncContext.updatePhase(PHASE_SUMMARY);
        String summaryId = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
        SyncSummaryItem currentSyncSummaryItem = new SyncSummaryItem(summaryId, uploadCount, uploadSize, deleteCount,
                deleteSize, Instant.now());
        try {
            if (uploadCount == 0 && deleteCount == 0) {
                log.debug("Skipping summary persistence because no uploads or deletions were recorded");
                return;
            }
            SyncSummaryItem existingSyncSummary = syncSummaryRepository.findById(summaryId)
                    .orElseGet(() -> new SyncSummaryItem(summaryId, 0, 0, 0, 0, Instant.now()));
            SyncSummaryItem savedSummary = syncSummaryRepository.save(new SyncSummaryItem(summaryId,
                    existingSyncSummary.uploadCount() + currentSyncSummaryItem.uploadCount(),
                    existingSyncSummary.uploadSize() + currentSyncSummaryItem.uploadSize(),
                    existingSyncSummary.deleteCount() + currentSyncSummaryItem.deleteCount(),
                    existingSyncSummary.deleteSize() + currentSyncSummaryItem.deleteSize(), Instant.now()));
            log.info("Persisted daily sync summary {}", savedSummary);
        } finally {
            notificationService.notifySummary(currentSyncSummaryItem, scanlocationconfig);
        }
    }

    private void startCloudCleanup(ScanLocationConfig locationConfig) throws CloudBackupException {
        CloudSyncContext.updatePhase(PHASE_CLEANUP);
        log.info("Starting cloud cleanup with provider {}", applicationProperties.getCloudProviderConfig().getType());
        catalogCount.set(0);
        catalogSize.set(0);
        if (isScanFolderEmpty(locationConfig)) {
            if (!locationConfig.isDeleteIfEmptyEnabled()) {
                log.warn("SAFETY GUARD: Scan folder '{}' appears to be empty. Skipping cloud cleanup to prevent unintended mass deletion. If this is intentional, set 'deleteIfEmptyEnabled: true' (env: APPLICATION_SCANFOLDERS_N_DELETEIFEMPTYENABLED=true) for this location.",
                        locationConfig.getScanFolder());
                ScanLocationConfig warningConfig = new ScanLocationConfig(locationConfig);
                notificationService.notifyError(
                        "Cleanup skipped — source folder is empty. Set deleteIfEmptyEnabled=true to allow deletion when folder is empty.",
                        warningConfig);
                return;
            }
            log.info("Scan folder '{}' is empty and deleteIfEmptyEnabled=true — proceeding with cloud cleanup.",
                    locationConfig.getScanFolder());
        }
        performBucketCleanup(PageRequest.ofSize(locationConfig.getCollectionFetchSize()), locationConfig);
        log.info("Completed cloud cleanup with {} deleted items and {} bytes", catalogCount.get(), catalogSize.get());
    }

    boolean isScanFolderEmpty(ScanLocationConfig locationConfig) {
        Path folder = Paths.get(locationConfig.getScanFolder());
        if (!Files.exists(folder)) {
            log.warn("Scan folder '{}' does not exist — treating as empty for safety.", locationConfig.getScanFolder());
            return true;
        }
        try (Stream<Path> entries = Files.walk(folder)) {
            return entries.filter(p -> !p.equals(folder))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .isEmpty();
        } catch (IOException e) {
            log.warn("Could not determine if scan folder '{}' is empty, assuming empty for safety: {}",
                    locationConfig.getScanFolder(), e.getMessage());
            return true;
        }
    }

    private void performBucketCleanup(Pageable catalogPages, ScanLocationConfig locationConfig) {
        log.debug("Loading cleanup batch page={} size={}", catalogPages.getPageNumber(), catalogPages.getPageSize());
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        Page<FileCatalogItem> catalogEntriesByPages;
        if (Optional.ofNullable(locationConfig.getStandardDeleteDaysLimit()).isPresent()) {
            Date since = Date.from(Instant.now().minus(Duration.ofDays(locationConfig.getStandardDeleteDaysLimit())));
            Date olderThan = Date.from(Instant.now().minus(
                    Duration.ofDays(locationConfig.getArchiveDeleteDaysHold() + locationConfig.getStandardDeleteDaysLimit())));
            log.debug("Cleanup retention window since={} olderThan={}", since, olderThan);
            catalogEntriesByPages = fileCatalogItemRepository
                    .findByParentFolderStartsWithAndArchiveDateAfterOrParentFolderStartsWithAndArchiveDateBefore(
                            rootFolder, since, rootFolder, olderThan, catalogPages);
        } else {
            catalogEntriesByPages = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder, catalogPages);
        }
        log.trace("Cleanup batch contains {} items (page {}/{})", catalogEntriesByPages.getNumberOfElements(),
                catalogEntriesByPages.getNumber() + 1, catalogEntriesByPages.getTotalPages());
        processCatalogEntryForCleanup(catalogEntriesByPages.getContent().stream());
        if (catalogEntriesByPages.hasNext()) {
            performBucketCleanup(catalogEntriesByPages.nextPageable(), locationConfig);
        }
    }

    private String fixLocationPath(String locationPath) {
        CharSequence charSquence = new SimpleMessage("\\\\");
        return locationPath.replace(charSquence, "\\");
    }

    private void processCatalogEntryForCleanup(Stream<FileCatalogItem> catalogStream) {
        catalogStream
                .filter(this::isFileNotExistOnDisk)
                .forEach(this::handleFileCatalogItemDelete);
    }

    protected String getCrC32C(String absolutePath) throws IOException {
        return folderBackupService.getCrC32C(absolutePath);
    }

    private boolean isFileNotExistOnDisk(FileCatalogItem filecatalogitem) {
        return Files.notExists(Paths.get(filecatalogitem.absolutePath()));
    }

    private void handleFileCatalogItemDelete(FileCatalogItem filecatalogitem) {
        try {
            Instant deleteStart = Instant.now();
            long startTime = System.nanoTime();
            cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType())
                    .delete(filecatalogitem);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            log.info("Deleted {} ({} bytes) in {} ms", filecatalogitem.absolutePath(), filecatalogitem.fileSize(),
                    durationMs);
            fileCatalogItemRepository.delete(filecatalogitem);
            thumbnailService.deleteThumbnail(filecatalogitem);
            catalogCount.incrementAndGet();
            catalogSize.addAndGet(filecatalogitem.fileSize());
            cloudSyncMetricsService.current().filesDeletedCounter().increment();
            cloudSyncMetricsService.current().deleteTimer().record(Duration.between(deleteStart, Instant.now()));
        } catch (Exception e) {
            log.error(
                    "Unable to delete {} from the cloud provider {}, item will be preserved in the catalog database for next iteration attempt",
                    filecatalogitem.absolutePath(), applicationProperties.getCloudProviderConfig().getType(), e);
        }
    }
}
