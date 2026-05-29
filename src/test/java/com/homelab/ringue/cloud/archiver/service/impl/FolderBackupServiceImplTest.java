package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.slf4j.MDC;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProvider;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviders;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.CloudProviderConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.BackupPipelineContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.NotificationService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;

class FolderBackupServiceImplTest {

    private static final String TEST_SCAN_FOLDER = "/test/scan/folder/";
    private static final String CRC32C = "MOCKCRC32C";

    private FolderBackupServiceImpl service;

    @Mock
    private FileCatalogItemRepository fileCatalogItemRepository;
    @Mock
    private FileCatalogItemMapper fileCatalogItemMapper;
    @Mock
    private CloudProviderFactory cloudProviderFactory;
    @Mock
    private ApplicationProperties applicationProperties;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Counter filesUploadedCounter;
    @Mock
    private Timer uploadTimer;
    @Mock
    private DistributionSummary gcpUploadBytesSummary;
    @Mock
    private CloudProviderConfig cloudProviderConfig;
    @Mock
    private CloudProvider cloudProvider;
    @Spy
    private ScanLocationConfig scanLocationConfig = new ScanLocationConfig();

    @BeforeEach
    void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        service = Mockito.spy(new FolderBackupServiceImpl(
                fileCatalogItemRepository,
                fileCatalogItemMapper,
                cloudProviderFactory,
                applicationProperties,
                notificationService,
                filesUploadedCounter,
                uploadTimer,
                gcpUploadBytesSummary));
        scanLocationConfig.setScanFolder(TEST_SCAN_FOLDER);
        scanLocationConfig.setCollectionFetchSize(50);
        Mockito.when(applicationProperties.getCloudProviderConfig()).thenReturn(cloudProviderConfig);
        Mockito.when(cloudProviderConfig.getType()).thenReturn(CloudProviders.NO_PROVIDER);
        Mockito.when(cloudProviderFactory.getCloudProvider(Mockito.any())).thenReturn(cloudProvider);
        Mockito.doReturn(CRC32C).when(service).getCrC32C(Mockito.anyString());
    }

    @Test
    void applyFilteringRulesIgnoresHiddenFilesWhenConfigured() {
        FileCatalogItem fileCatalogItem = new FileCatalogItem(TEST_SCAN_FOLDER + ".hidden", ".hidden", null,
                TEST_SCAN_FOLDER, false, null, null, null, null);
        scanLocationConfig.setIgnoreHiddenFiles(true);
        scanLocationConfig.setCompiledIgnorePatterns(List.of());

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.isHidden(Path.of(fileCatalogItem.absolutePath()))).thenReturn(true);

            assertFalse(service.applyFilteringRules(scanLocationConfig, fileCatalogItem));
        }
    }

    @Test
    void applyFilteringRulesIgnoresConfiguredPatterns() {
        FileCatalogItem fileCatalogItem = new FileCatalogItem(TEST_SCAN_FOLDER + "thumbs.db", "thumbs.db", "db",
                TEST_SCAN_FOLDER, false, null, null, null, null);
        scanLocationConfig.setIgnoreHiddenFiles(false);
        scanLocationConfig.setCompiledIgnorePatterns(List.of(java.util.regex.Pattern.compile("thumbs\\.db")));

        assertFalse(service.applyFilteringRules(scanLocationConfig, fileCatalogItem));
    }

    @Test
    void getFileToProcessIfAnyReturnsNullWhenLastModifiedMatches() {
        Instant modifiedAt = Instant.now();
        FileCatalogItem existing = new FileCatalogItem(TEST_SCAN_FOLDER + "same.jpg", "same.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 10L, null, CRC32C, modifiedAt);
        Map<String, FileCatalogItem> cache = new HashMap<>();
        cache.put(existing.absolutePath(), existing);

        FileCatalogItem result = service.getFileToProcessIfAny(cache, existing);

        assertNull(result);
        Mockito.verify(service, Mockito.never()).getCrC32CPopulatedItem(Mockito.any());
    }

    @Test
    void getFileToProcessIfAnyReturnsNullAndUpdatesMetadataWhenChecksumMatches() {
        Instant existingModified = Instant.now().minusSeconds(60);
        Instant currentModified = Instant.now();
        FileCatalogItem existing = new FileCatalogItem(TEST_SCAN_FOLDER + "same-crc.jpg", "same-crc.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 10L, null, CRC32C, existingModified);
        FileCatalogItem fileOnDisk = new FileCatalogItem(existing.absolutePath(), existing.fileName(), existing.fileExtension(),
                existing.parentFolder(), false, existing.fileSize(), null, null, currentModified);
        FileCatalogItem crcPopulated = new FileCatalogItem(existing.absolutePath(), existing.fileName(), existing.fileExtension(),
                existing.parentFolder(), false, existing.fileSize(), null, CRC32C, currentModified);
        FileCatalogItem updatedMetadata = new FileCatalogItem(existing.absolutePath(), existing.fileName(), existing.fileExtension(),
                existing.parentFolder(), false, existing.fileSize(), null, CRC32C, currentModified);
        Map<String, FileCatalogItem> cache = new HashMap<>();
        cache.put(existing.absolutePath(), existing);
        Mockito.doReturn(crcPopulated).when(service).getCrC32CPopulatedItem(fileOnDisk);
        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemUpdateLastModified(existing, currentModified))
                .thenReturn(updatedMetadata);

        FileCatalogItem result = service.getFileToProcessIfAny(cache, fileOnDisk);

        assertNull(result);
        assertEquals(updatedMetadata, cache.get(existing.absolutePath()));
    }

    @Test
    void getFileToProcessIfAnyReturnsFileWhenChecksumChanges() {
        Instant currentModified = Instant.now();
        FileCatalogItem existing = new FileCatalogItem(TEST_SCAN_FOLDER + "changed.jpg", "changed.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 10L, null, CRC32C, currentModified.minusSeconds(60));
        FileCatalogItem fileOnDisk = new FileCatalogItem(existing.absolutePath(), existing.fileName(), existing.fileExtension(),
                existing.parentFolder(), false, existing.fileSize(), null, null, currentModified);
        FileCatalogItem crcPopulated = new FileCatalogItem(existing.absolutePath(), existing.fileName(), existing.fileExtension(),
                existing.parentFolder(), false, existing.fileSize(), null, CRC32C + "-new", currentModified);
        Map<String, FileCatalogItem> cache = new HashMap<>();
        cache.put(existing.absolutePath(), existing);
        Mockito.doReturn(crcPopulated).when(service).getCrC32CPopulatedItem(fileOnDisk);

        FileCatalogItem result = service.getFileToProcessIfAny(cache, fileOnDisk);

        assertEquals(crcPopulated, result);
    }

    @Test
    void processFileStreamForBackupUploadsCandidatesAndSavesResidualMetadata() {
        FileCatalogItem directory = new FileCatalogItem(TEST_SCAN_FOLDER + "nested", "nested", null,
                TEST_SCAN_FOLDER, true, 0L, null, null, Instant.now());
        FileCatalogItem ignored = new FileCatalogItem(TEST_SCAN_FOLDER + ".ignored", ".ignored", null,
                TEST_SCAN_FOLDER, false, 0L, null, null, Instant.now());
        FileCatalogItem upload = new FileCatalogItem(TEST_SCAN_FOLDER + "upload.jpg", "upload.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 20L, null, null, Instant.now());
        FileCatalogItem metadataOnly = new FileCatalogItem(TEST_SCAN_FOLDER + "metadata.jpg", "metadata.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 30L, null, null, Instant.now());
        FileCatalogItem leftover = new FileCatalogItem(TEST_SCAN_FOLDER + "leftover.jpg", "leftover.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 40L, null, CRC32C, Instant.now());
        BackupPipelineContext context = new BackupPipelineContext(new HashMap<>(Map.of(leftover.absolutePath(), leftover)),
                new java.util.concurrent.atomic.AtomicInteger(), new java.util.concurrent.atomic.AtomicLong());

        Mockito.when(fileCatalogItemMapper.mapFromPath(Mockito.any()))
                .thenReturn(directory)
                .thenReturn(ignored)
                .thenReturn(upload)
                .thenReturn(metadataOnly);
        Mockito.doReturn(false).when(service).applyFilteringRules(scanLocationConfig, directory);
        Mockito.doReturn(false).when(service).applyFilteringRules(scanLocationConfig, ignored);
        Mockito.doReturn(true).when(service).applyFilteringRules(scanLocationConfig, upload);
        Mockito.doReturn(true).when(service).applyFilteringRules(scanLocationConfig, metadataOnly);
        Mockito.doReturn(upload).when(service).getFileToProcessIfAny(context.catalogCache(), upload);
        Mockito.doReturn(null).when(service).getFileToProcessIfAny(context.catalogCache(), metadataOnly);
        Mockito.doNothing().when(service).performCloudBackup(scanLocationConfig, context, upload);

        service.processFileStreamForBackup(scanLocationConfig, context,
                Stream.of(Path.of("nested"), Path.of("ignored"), Path.of("upload"), Path.of("metadata")));

        Mockito.verify(service).performCloudBackup(scanLocationConfig, context, upload);
        Mockito.verify(fileCatalogItemRepository).save(leftover);
    }

    @Test
    void processFileStreamForBackupPreservesExistingMdcContext() throws Exception {
        FileCatalogItem upload = new FileCatalogItem(TEST_SCAN_FOLDER + "upload.jpg", "upload.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 20L, null, CRC32C, Instant.now());
        BackupPipelineContext context = new BackupPipelineContext(new ConcurrentHashMap<>(),
                new java.util.concurrent.atomic.AtomicInteger(), new java.util.concurrent.atomic.AtomicLong());

        Mockito.when(fileCatalogItemMapper.mapFromPath(Mockito.any())).thenReturn(upload);
        Mockito.doReturn(true).when(service).applyFilteringRules(scanLocationConfig, upload);
        Mockito.doReturn(upload).when(service).getFileToProcessIfAny(context.catalogCache(), upload);
        Mockito.doCallRealMethod().when(service).performCloudBackup(scanLocationConfig, context, upload);
        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(upload)).thenReturn(upload);
        MDC.put("requestId", "req-789");

        service.processFileStreamForBackup(scanLocationConfig, context, Stream.of(Path.of("upload")));

        Mockito.verify(cloudProvider).upload(upload);
        assertEquals("req-789", MDC.get("requestId"));
        assertNull(MDC.get("scanFolder"));
        assertNull(MDC.get("filePath"));
        assertNull(MDC.get("backupDecisionId"));
        MDC.clear();
    }

    @Test
    void performCloudBackupUploadsPersistsMetricsAndRestoresExistingMdc() throws Exception {
        FileCatalogItem fileCatalogItem = new FileCatalogItem(TEST_SCAN_FOLDER + "upload.jpg", "upload.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 20L, null, CRC32C, Instant.now());
        FileCatalogItem archived = new FileCatalogItem(fileCatalogItem.absolutePath(), fileCatalogItem.fileName(),
                fileCatalogItem.fileExtension(), fileCatalogItem.parentFolder(), false, fileCatalogItem.fileSize(),
                new Date(), fileCatalogItem.crc32c(), fileCatalogItem.lastModified());
        BackupPipelineContext context = new BackupPipelineContext(new HashMap<>(),
                new java.util.concurrent.atomic.AtomicInteger(), new java.util.concurrent.atomic.AtomicLong());
        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(fileCatalogItem)).thenReturn(archived);
        MDC.put("requestId", "req-123");

        service.performCloudBackup(scanLocationConfig, context, fileCatalogItem);

        Mockito.verify(cloudProvider).upload(archived);
        Mockito.verify(fileCatalogItemRepository).save(archived);
        Mockito.verify(gcpUploadBytesSummary).record(archived.fileSize());
        Mockito.verify(filesUploadedCounter).increment();
        assertEquals(1, context.uploadedCount().get());
        assertEquals(archived.fileSize(), context.uploadedSize().get());
        assertEquals("req-123", MDC.get("requestId"));
        assertNull(MDC.get("scanFolder"));
        assertNull(MDC.get("filePath"));
        assertNull(MDC.get("backupDecisionId"));
        MDC.clear();
    }

    @Test
    void performCloudBackupNotifiesOnFailureAndClearsMdc() throws Exception {
        FileCatalogItem fileCatalogItem = new FileCatalogItem(TEST_SCAN_FOLDER + "broken.jpg", "broken.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 20L, null, CRC32C, Instant.now());
        BackupPipelineContext context = new BackupPipelineContext(Collections.emptyMap(),
                new java.util.concurrent.atomic.AtomicInteger(), new java.util.concurrent.atomic.AtomicLong());
        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(fileCatalogItem)).thenReturn(fileCatalogItem);
        Mockito.doThrow(new RuntimeException("boom")).when(cloudProvider).upload(fileCatalogItem);
        MDC.put("requestId", "req-456");

        service.performCloudBackup(scanLocationConfig, context, fileCatalogItem);

        Mockito.verify(notificationService)
                .notifyError(Mockito.contains(fileCatalogItem.absolutePath()), Mockito.same(scanLocationConfig));
        assertEquals("req-456", MDC.get("requestId"));
        assertNull(MDC.get("scanFolder"));
        assertNull(MDC.get("filePath"));
        assertNull(MDC.get("backupDecisionId"));
        MDC.clear();
    }

    @Test
    void performCloudBackupRestoresSyncMdcEnvelope() throws Exception {
        FileCatalogItem fileCatalogItem = new FileCatalogItem(TEST_SCAN_FOLDER + "upload-again.jpg", "upload-again.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 20L, null, CRC32C, Instant.now());
        FileCatalogItem archived = new FileCatalogItem(fileCatalogItem.absolutePath(), fileCatalogItem.fileName(),
                fileCatalogItem.fileExtension(), fileCatalogItem.parentFolder(), false, fileCatalogItem.fileSize(),
                new Date(), fileCatalogItem.crc32c(), fileCatalogItem.lastModified());
        BackupPipelineContext context = new BackupPipelineContext(new HashMap<>(),
                new java.util.concurrent.atomic.AtomicInteger(), new java.util.concurrent.atomic.AtomicLong());
        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(fileCatalogItem)).thenReturn(archived);
        MDC.put("requestId", "req-789");

        service.performCloudBackup(scanLocationConfig, context, fileCatalogItem);

        assertEquals("req-789", MDC.get("requestId"));
        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
        assertNull(MDC.get("scanFolder"));
        assertNull(MDC.get("filePath"));
        assertNull(MDC.get("backupDecisionId"));
        MDC.clear();
    }

    @Test
    void backUpFolderLoadsCatalogProcessesStreamAndReturnsAggregatedContext() throws Exception {
        FileCatalogItem cached = new FileCatalogItem(TEST_SCAN_FOLDER + "cached.jpg", "cached.jpg", "jpg",
                TEST_SCAN_FOLDER, false, 1L, null, CRC32C, Instant.now());
        BackupPipelineContext processed = new BackupPipelineContext(new HashMap<>(Map.of(cached.absolutePath(), cached)),
                new java.util.concurrent.atomic.AtomicInteger(2), new java.util.concurrent.atomic.AtomicLong(42L));
        Mockito.when(fileCatalogItemRepository.findByParentFolderStartsWith(Mockito.anyString(), Mockito.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        Mockito.doAnswer(invocation -> {
            BackupPipelineContext context = invocation.getArgument(1);
            context.uploadedCount().set(processed.uploadedCount().get());
            context.uploadedSize().set(processed.uploadedSize().get());
            return null;
        }).when(service).processFileStreamForBackup(Mockito.eq(scanLocationConfig), Mockito.any(), Mockito.any());

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.walk(Path.of(TEST_SCAN_FOLDER))).thenReturn(Stream.of(Path.of(TEST_SCAN_FOLDER + "a.jpg")));

            BackupPipelineContext result = service.backUpFolder(scanLocationConfig);

            assertNotNull(result);
            assertEquals(2, result.uploadedCount().get());
            assertEquals(42L, result.uploadedSize().get());
        }
    }
}
