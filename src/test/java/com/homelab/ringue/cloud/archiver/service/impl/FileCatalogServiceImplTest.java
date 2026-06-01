package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.slf4j.MDC;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviders;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.CloudProviderConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.repository.SyncSummaryRepository;
import com.homelab.ringue.cloud.archiver.service.BackupPipelineContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetrics;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetricsService;
import com.homelab.ringue.cloud.archiver.service.CloudSyncOrchestrator;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.FolderBackupService;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
import com.homelab.ringue.cloud.archiver.service.SyncLockManager;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;

class FileCatalogServiceImplTest {

    private static final String TEST_SCAN_FOLDER = "/test/scan/folder/";

    private FileCatalogServiceImpl serviceImplSpy;

    @Mock
    private FileCatalogItemRepository fileCatalogItemRepository;
    @Mock
    private SyncSummaryRepository summaryRepository;
    @Mock
    private ApplicationProperties applicationProperties;
    @Spy
    private ScanLocationConfig scanLocationConfigMock = new ScanLocationConfig();
    @Mock
    private CloudProviderConfig cloudProviderConfig;
    @Mock
    private FileCatalogItemMapper fileCatalogItemMapper;
    @Mock
    private CloudProviderFactory cloudProviderFactory;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SyncLockManager syncLockManager;
    @Mock
    private CloudSyncMetricsService cloudSyncMetricsService;
    @Mock
    private CloudSyncMetrics cloudSyncMetrics;
    @Mock
    private CloudSyncOrchestrator cloudSyncOrchestrator;
    @Mock
    private FolderBackupService folderBackupService;
    @Mock
    private ThumbnailService thumbnailService;

    @BeforeEach
    void setupTests() throws Exception {
        MockitoAnnotations.openMocks(this);

        serviceImplSpy = Mockito.spy(new FileCatalogServiceImpl(
                fileCatalogItemRepository,
                fileCatalogItemMapper,
                cloudProviderFactory,
                applicationProperties,
                summaryRepository,
                notificationService,
                cloudSyncMetricsService,
                cloudSyncOrchestrator,
                folderBackupService,
                thumbnailService));

        Mockito.when(scanLocationConfigMock.getScanFolder()).thenReturn(TEST_SCAN_FOLDER);
        Mockito.when(applicationProperties.getCloudProviderConfig()).thenReturn(cloudProviderConfig);
        Mockito.when(cloudProviderConfig.getType()).thenReturn(CloudProviders.NO_PROVIDER);
        Mockito.when(cloudSyncMetricsService.current()).thenReturn(cloudSyncMetrics);
        Mockito.when(cloudSyncMetricsService.reset()).thenReturn(cloudSyncMetrics);
        Mockito.doAnswer(invocation ->
                new BackupPipelineContext(new HashMap<>(), new java.util.concurrent.atomic.AtomicInteger(),
                        new java.util.concurrent.atomic.AtomicLong()))
                .when(folderBackupService).backUpFolder(Mockito.any());
    }

    @AfterEach
    void clearMdc() {
        CloudSyncContext.clear();
    }

    @Test
    void performLocationSyncDelegatesToOrchestrator() throws Exception {
        serviceImplSpy.performLocationSync(scanLocationConfigMock);

        Mockito.verify(cloudSyncOrchestrator).performLocationSync(scanLocationConfigMock);
    }

    @Test
    void performLocationSyncClearsMdcAfterExecution() throws Exception {
        serviceImplSpy.performLocationSync(scanLocationConfigMock);

        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    @Test
    void startAllLocationSyncsResetsMetricsAndDelegatesToOrchestrator() {
        Mockito.when(cloudSyncOrchestrator.startAllLocationSyncs()).thenReturn(true);

        assertTrue(serviceImplSpy.startAllLocationSyncs());

        Mockito.verify(cloudSyncMetricsService).reset();
        Mockito.verify(cloudSyncOrchestrator).startAllLocationSyncs();
    }

    @Test
    void downloadFromCloudReturnsFalseAndClearsMdcWhenCloudProviderUnavailable() {
        Mockito.when(applicationProperties.getDownloadRoot()).thenReturn(TEST_SCAN_FOLDER);

        org.junit.jupiter.api.Assertions.assertFalse(serviceImplSpy.downloadFromCloud("downloaded.jpg"));

        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    @Test
    void startCloudBackupCapturesFolderBackupCounters() throws Exception {
        BackupPipelineContext pipelineContext = new BackupPipelineContext(
                new HashMap<>(),
                new java.util.concurrent.atomic.AtomicInteger(4),
                new java.util.concurrent.atomic.AtomicLong(512L));
        Mockito.doReturn(pipelineContext).when(folderBackupService).backUpFolder(scanLocationConfigMock);

        try (CloudSyncContext.Scope ignored = CloudSyncContext.open(CloudSyncContext.newRunId(), scanLocationConfigMock, "seed")) {
            SyncSummaryItem backupSummary = serviceImplSpy.executeBackup(scanLocationConfigMock);

            assertEquals(4, backupSummary.uploadCount());
            assertEquals(512L, backupSummary.uploadSize());
            Mockito.verify(folderBackupService).backUpFolder(scanLocationConfigMock);
            assertEquals("backup", MDC.get(CloudSyncContext.PHASE_KEY));
        }
    }

    @Test
    void persistSummarySkipsRepositoryWriteWhenNoChanges() {
        SyncSummaryItem emptySummary = new SyncSummaryItem(null, 0, 0L, 0, 0L, java.time.Instant.now());

        serviceImplSpy.persistSummary(emptySummary, scanLocationConfigMock);

        Mockito.verify(summaryRepository, Mockito.never()).save(Mockito.any());
        Mockito.verify(notificationService).notifySummary(Mockito.any(), Mockito.eq(scanLocationConfigMock));
    }

    @Test
    void isScanFolderEmptyReturnsTrueWhenFolderDoesNotExist() {
        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            java.nio.file.Path folder = java.nio.file.Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> java.nio.file.Files.exists(folder)).thenReturn(false);

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock));
        }
    }

    @Test
    void isScanFolderEmptyReturnsTrueWhenFolderHasNoFiles() throws IOException {
        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            java.nio.file.Path folder = java.nio.file.Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> java.nio.file.Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> java.nio.file.Files.walk(folder)).thenReturn(Stream.of(folder));
            mockedFiles.when(() -> java.nio.file.Files.isRegularFile(folder)).thenReturn(false);

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock));
        }
    }

    @Test
    void isScanFolderEmptyReturnsFalseWhenFolderHasAtLeastOneFile() throws IOException {
        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            java.nio.file.Path folder = java.nio.file.Path.of(TEST_SCAN_FOLDER);
            java.nio.file.Path file = java.nio.file.Path.of(TEST_SCAN_FOLDER + "photo.jpg");
            mockedFiles.when(() -> java.nio.file.Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> java.nio.file.Files.walk(folder)).thenReturn(Stream.of(folder, file));
            mockedFiles.when(() -> java.nio.file.Files.isRegularFile(folder)).thenReturn(false);
            mockedFiles.when(() -> java.nio.file.Files.isRegularFile(file)).thenReturn(true);

            org.junit.jupiter.api.Assertions.assertFalse(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock));
        }
    }

    @Test
    void isScanFolderEmptyReturnsTrueWhenWalkThrowsIOException() throws IOException {
        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            java.nio.file.Path folder = java.nio.file.Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> java.nio.file.Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> java.nio.file.Files.walk(folder)).thenThrow(new IOException("boom"));

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock));
        }
    }
}
