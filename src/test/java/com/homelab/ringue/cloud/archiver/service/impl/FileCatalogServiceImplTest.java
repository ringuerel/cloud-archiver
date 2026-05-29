package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProvider;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviders;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.CloudProviderConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.PendingDeletionItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildMode;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildSummary;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.repository.SyncSummaryRepository;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;
import com.homelab.ringue.cloud.archiver.service.SyncLockManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MeterRegistry.Config;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class FileCatalogServiceImplTest {

    private static final String TEST_SCAN_FOLDER = "/test/scan/folder/";
    private static final String CRC32C = "MOCKCRC32C";

    private FileCatalogServiceImpl serviceImplSpy;

    @Mock
    private FileCatalogItemRepository fileCatalogItemRepository;
    
    @Mock
    private SyncSummaryRepository summaryRepository;

    @Mock
    private ApplicationProperties applicationProperties;

    @Spy
    private ScanLocationConfig scanLocationConfigMock;

    @Mock
    private CloudProviderConfig cloudProviderConfig;

    @Mock
    private Page<FileCatalogItem> fileCatalogPageMock;

    @Mock
    private FileCatalogItemMapper fileCatalogItemMapper;

    @Mock
    private CloudProviderFactory cloudProviderFactory;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private CloudProvider cloudProvider;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Config meterRegistryConfig;

    @Mock
    private Timer scanDurationTimerMock;

    @Mock
    private SyncLockManager syncLockManager;

    @BeforeEach
    public void setupTests() throws IOException{
        MockitoAnnotations.openMocks(this);
        Mockito.when(meterRegistry.config()).thenReturn(meterRegistryConfig);
        Mockito.when(meterRegistry.timer(Mockito.anyString())).thenReturn(scanDurationTimerMock);
        Mockito.doNothing().when(scanDurationTimerMock).record(Mockito.any(java.time.Duration.class));

        // Inject mocked SyncLockManager into the service constructor
        serviceImplSpy = Mockito.spy(new FileCatalogServiceImpl(
            fileCatalogItemRepository,
            fileCatalogItemMapper,
            cloudProviderFactory,
            applicationProperties,
            summaryRepository,
            notificationService,
            thumbnailService,
            syncLockManager,
            meterRegistry
        ));
        // Inject mock scanDurationTimer into serviceImplSpy using reflection
        try {
            java.lang.reflect.Field scanDurationTimerField = FileCatalogServiceImpl.class.getDeclaredField("scanDurationTimer");
            scanDurationTimerField.setAccessible(true);
            scanDurationTimerField.set(serviceImplSpy, scanDurationTimerMock);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject scanDurationTimer mock", e);
        }
        // Default behavior for tests
        Mockito.when(syncLockManager.acquireLock(Mockito.anyLong())).thenReturn(true);
        Mockito.when(scanLocationConfigMock.getScanFolder()).thenReturn(TEST_SCAN_FOLDER);
        Mockito.when(applicationProperties.getCloudProviderConfig()).thenReturn(cloudProviderConfig);
        Mockito.when(cloudProviderConfig.getType()).thenReturn(CloudProviders.NO_PROVIDER);
        Mockito.when(cloudProviderFactory.getCloudProvider(Mockito.any())).thenReturn(cloudProvider);
        Mockito.when(fileCatalogItemRepository.findByParentFolderStartsWith(Mockito.anyString(),Mockito.any())).thenReturn(fileCatalogPageMock);
        Mockito.doReturn(CRC32C).when(serviceImplSpy).getCrC32C(Mockito.anyString());
    }


    @Test
    void testApplyFilteringRules() {
        FileCatalogItem fileCatalogItem = new FileCatalogItem("C:\\folder1\\.git", ".git", null, "C:\\folder1", true, null, null,null,null);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(Arrays.asList(scanLocationConfigMock));
        Mockito.when(scanLocationConfigMock.getIgnorePatterns()).thenReturn(Arrays.asList("^\\..+"));
        Mockito.when(scanLocationConfigMock.getCompiledIgnorePatterns()).thenReturn(Collections.emptyList());
        boolean shouldBeIgnored = serviceImplSpy.applyFilteringRules(scanLocationConfigMock,fileCatalogItem);
        assertTrue(shouldBeIgnored);
    }

    @ParameterizedTest
    @CsvSource({
        "true",
        "false"
    })
    void testPerformLocationSyncInvokesCleanupBasedOnConfig(boolean cleanRemovedFromCloud) throws CloudBackupException{
        Mockito.when(scanLocationConfigMock.isCleanRemovedFromCloud()).thenReturn(cleanRemovedFromCloud);
        // Folder is not empty — guard must not block cleanup
        Mockito.doReturn(false).when(serviceImplSpy).isScanFolderEmpty(scanLocationConfigMock);
        try(MockedStatic<Files> mockedFiles = mockStatic(Files.class)){
            Path directory = Path.of(TEST_SCAN_FOLDER);
            Stream<Path> mockStream = Arrays.asList("file1.jpg").stream().map(childName -> Path.of(TEST_SCAN_FOLDER+childName));
            // Define the behavior of the mocked Files.walk method
            mockedFiles.when(() -> Files.walk(directory))
                       .thenReturn(mockStream);
            serviceImplSpy.performLocationSync(scanLocationConfigMock);
        }
        Mockito.verify(fileCatalogItemRepository,Mockito.times(cleanRemovedFromCloud?2:1)).findByParentFolderStartsWith(Mockito.anyString(),Mockito.any());
        Mockito.verify(serviceImplSpy).processFileStreamForBackup(Mockito.any(),Mockito.any(),Mockito.any());
        Mockito.verify(notificationService,Mockito.times(cleanRemovedFromCloud?2:1)).notifyInfoMessage(Mockito.anyString(),Mockito.any(ScanLocationConfig.class));
    }


    @Test
    void processFileStreamForBackupIgnoresBackedUpItems() throws IOException{
        FileCatalogItem someFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,CRC32C, Instant.now());
        FileCatalogItem otherFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/otherFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 70L, null,null, Instant.now());
        FileCatalogItem someMovFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/someMovFile.mov", "someMovFile.mov", "mov", TEST_SCAN_FOLDER, false, 250L, null,CRC32C, Instant.now());
        FileCatalogItem someFolder = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFolder", "someMovFile", null, TEST_SCAN_FOLDER, true, 0L, null,null, Instant.now());
        Stream<Path> filesStream = prepareFilesStream(Arrays.asList(
            someFile.absolutePath(),
            otherFile.absolutePath(),
            someMovFile.absolutePath(),
            someFolder.absolutePath()
            ));
        
        Mockito.when(fileCatalogItemMapper.mapFromPath(Mockito.any()))
        .thenReturn(someFolder)
        .thenReturn(someFile)
        .thenReturn(otherFile)
        .thenReturn(someMovFile);
        //This should have it's own test
        Mockito.doNothing().when(serviceImplSpy).performCloudBackup(Mockito.any());
        Mockito.doReturn(someFile).when(serviceImplSpy).getCrC32CPopulatedItem(someFile);
        Mockito.doReturn(otherFile).when(serviceImplSpy).getCrC32CPopulatedItem(otherFile);
        Mockito.doReturn(someMovFile).when(serviceImplSpy).getCrC32CPopulatedItem(someMovFile);
        Map<String, FileCatalogItem> backedUpItems = new HashMap<>();
        backedUpItems.put(someMovFile.absolutePath(), someMovFile);
        serviceImplSpy.processFileStreamForBackup(scanLocationConfigMock, backedUpItems, filesStream);
        Mockito.verify(serviceImplSpy,Mockito.times(2)).performCloudBackup(Mockito.any());
    }

    @Test
    void processFileStreamForBackupIgnoresWhenLastModifiedDateHasNotChanged() throws IOException{
        FileCatalogItem someFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,null,Instant.now());
        FileCatalogItem updatedFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/otherFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 70L, null,null,Instant.now());
        Stream<Path> filesStream = prepareFilesStream(Arrays.asList(
            someFile.absolutePath(),
            updatedFile.absolutePath()
            ));

        Mockito.doReturn(someFile).when(serviceImplSpy).getFileToProcessIfAny(Mockito.any(),Mockito.any());
        
        Mockito.when(fileCatalogItemMapper.mapFromPath(Mockito.any()))
        .thenReturn(someFile)
        .thenReturn(updatedFile);
        //This should have it's own test
        Mockito.doNothing().when(serviceImplSpy).performCloudBackup(Mockito.any());
        Map<String, FileCatalogItem> backedUpItems = new HashMap<>();
        backedUpItems.put(updatedFile.absolutePath(), updatedFile);
        backedUpItems.put(someFile.absolutePath(), someFile); 
        Mockito.doReturn(null).when(serviceImplSpy).getFileToProcessIfAny(backedUpItems, updatedFile);
        serviceImplSpy.processFileStreamForBackup(scanLocationConfigMock, backedUpItems, filesStream);
        Mockito.verify(serviceImplSpy).performCloudBackup(Mockito.any());
    }

    @ParameterizedTest
    @MethodSource("existingAndUnmodifiedItems")
    void getFileToProcessIfAnyshouldReturnNullWhenUnmodified(Map<String, FileCatalogItem> collectionIdsInMemoryCache,FileCatalogItem fileCatalogItem) throws IOException{
        Mockito.doReturn(fileCatalogItem).when(serviceImplSpy).getCrC32CPopulatedItem(fileCatalogItem);
        //Returns smething with null crc32
        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemUpdateCheckSum(fileCatalogItem, null)).thenReturn(new FileCatalogItem(CRC32C, CRC32C, CRC32C, TEST_SCAN_FOLDER, false, null, null, null, null));
        FileCatalogItem fileToProcessIfAny = serviceImplSpy.getFileToProcessIfAny(new HashMap<>(collectionIdsInMemoryCache), fileCatalogItem);
        assertNull(fileToProcessIfAny);
        assertEquals(2,collectionIdsInMemoryCache.size());
    }

    @ParameterizedTest
    @MethodSource("newAndModifiedItems")
    void getFileToProcessIfAnyshouldReturnFileItemWhenNewOrModifiedFile(Map<String, FileCatalogItem> collectionIdsInMemoryCache,FileCatalogItem fileCatalogItem) throws IOException{
        //Returns new CRC32
        Mockito.doReturn(new FileCatalogItem(fileCatalogItem.absolutePath(), fileCatalogItem.fileName(), fileCatalogItem.fileExtension(), fileCatalogItem.parentFolder(), false, null, null, CRC32C+"NEW", null)).when(serviceImplSpy).getCrC32CPopulatedItem(fileCatalogItem);
        //Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemUpdateCheckSum(Mockito.eq(fileCatalogItem), Mockito.anyString())).thenReturn(new FileCatalogItem(CRC32C, CRC32C, CRC32C, TEST_SCAN_FOLDER, false, null, null, null, null));
        FileCatalogItem fileToProcessIfAny = serviceImplSpy.getFileToProcessIfAny(new HashMap<>(collectionIdsInMemoryCache), fileCatalogItem);
        assertNotNull(fileToProcessIfAny);
    }

    static Stream<? extends Arguments> existingAndUnmodifiedItems() {
        FileCatalogItem existingUnModifiedFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile1.jpg", "someFile1.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,null,Instant.now());
        FileCatalogItem existingPreviousVersion = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,CRC32C,Instant.now().minusMillis(60000L));
        FileCatalogItem existingUnModifiedFileButChangeDate = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,CRC32C,Instant.now());
        Map<String, FileCatalogItem> inMemmoryItems = new HashMap<>();
        inMemmoryItems.put(existingUnModifiedFile.absolutePath(), existingUnModifiedFile);
        inMemmoryItems.put(existingPreviousVersion.absolutePath(), existingPreviousVersion);
        return Stream.of(
          Arguments.of(inMemmoryItems,existingUnModifiedFile),
          Arguments.of(inMemmoryItems,existingUnModifiedFileButChangeDate)
        );
    }

    static Stream<? extends Arguments> newAndModifiedItems() {
        FileCatalogItem newItem = new FileCatalogItem(TEST_SCAN_FOLDER+"/someNewFile.jpg", "someNewFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,null,Instant.now());
        FileCatalogItem existingPreviousVersion = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,CRC32C,Instant.now().minusMillis(06000L));
        FileCatalogItem modifiedVersion = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,null,Instant.now());
        Map<String, FileCatalogItem> inMemmoryItems = new HashMap<>();
        inMemmoryItems.put(existingPreviousVersion.absolutePath(), existingPreviousVersion);
        return Stream.of(
          Arguments.of(inMemmoryItems,newItem),
          Arguments.of(inMemmoryItems,modifiedVersion)
        );
    }


    @Test
    void resetMetricsRemovesAndReRegistersMeters() throws Exception {
        SimpleMeterRegistry simpleRegistry = Mockito.spy(new SimpleMeterRegistry());
        FileCatalogServiceImpl metricsService = new FileCatalogServiceImpl(
            fileCatalogItemRepository,
            fileCatalogItemMapper,
            cloudProviderFactory,
            applicationProperties,
            summaryRepository,
            notificationService,
            thumbnailService,
            syncLockManager,
            simpleRegistry
        );

        MetricsSnapshot beforeReset = captureMetrics(metricsService);

        Method resetMetricsMethod = FileCatalogServiceImpl.class.getDeclaredMethod("resetMetrics");
        resetMetricsMethod.setAccessible(true);
        resetMetricsMethod.invoke(metricsService);

        MetricsSnapshot afterReset = captureMetrics(metricsService);

        assertNotSame(beforeReset.filesUploadedCounter(), afterReset.filesUploadedCounter());
        assertNotSame(beforeReset.filesDeletedCounter(), afterReset.filesDeletedCounter());
        assertNotSame(beforeReset.uploadTimer(), afterReset.uploadTimer());
        assertNotSame(beforeReset.deleteTimer(), afterReset.deleteTimer());
        assertNotSame(beforeReset.scanDurationTimer(), afterReset.scanDurationTimer());
        assertNotSame(beforeReset.filesInCatalogGauge(), afterReset.filesInCatalogGauge());
        assertNotSame(beforeReset.gcpDownloadsCounter(), afterReset.gcpDownloadsCounter());
        assertNotSame(beforeReset.gcpUploadBytesSummary(), afterReset.gcpUploadBytesSummary());
        assertNotSame(beforeReset.gcpDownloadBytesSummary(), afterReset.gcpDownloadBytesSummary());

        Mockito.verify(simpleRegistry, Mockito.atLeast(9)).remove(Mockito.any(Meter.class));
    }

    private MetricsSnapshot captureMetrics(FileCatalogServiceImpl target) throws ReflectiveOperationException {
        return new MetricsSnapshot(
            getMetricField(target, "filesUploadedCounter"),
            getMetricField(target, "filesDeletedCounter"),
            getMetricField(target, "uploadTimer"),
            getMetricField(target, "deleteTimer"),
            getMetricField(target, "scanDurationTimer"),
            getMetricField(target, "filesInCatalogGauge"),
            getMetricField(target, "gcpDownloadsCounter"),
            getMetricField(target, "gcpUploadBytesSummary"),
            getMetricField(target, "gcpDownloadBytesSummary")
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T getMetricField(FileCatalogServiceImpl target, String fieldName) throws ReflectiveOperationException {
        Field field = FileCatalogServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private record MetricsSnapshot(
        Counter filesUploadedCounter,
        Counter filesDeletedCounter,
        Timer uploadTimer,
        Timer deleteTimer,
        Timer scanDurationTimer,
        Gauge filesInCatalogGauge,
        Counter gcpDownloadsCounter,
        DistributionSummary gcpUploadBytesSummary,
        DistributionSummary gcpDownloadBytesSummary
    ) {}

    private Stream<Path> prepareFilesStream(List<String> filesPaths) {
        return filesPaths.stream().map(Path::of);
    }

    // -------------------------------------------------------------------------
    // isScanFolderEmpty tests
    // -------------------------------------------------------------------------

    @Test
    void isScanFolderEmpty_returnsTrueWhenFolderDoesNotExist() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path folder = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.exists(folder)).thenReturn(false);

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock),
                "A non-existent folder should be treated as empty for safety");
        }
    }

    @Test
    void isScanFolderEmpty_returnsTrueWhenFolderHasNoFiles() throws IOException {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path folder = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> Files.walk(folder)).thenReturn(Stream.of(folder));
            mockedFiles.when(() -> Files.isRegularFile(folder)).thenReturn(false);

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock),
                "A folder containing only directories (no regular files) should be considered empty");
        }
    }

    @Test
    void isScanFolderEmpty_returnsFalseWhenFolderHasAtLeastOneFile() throws IOException {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path folder = Path.of(TEST_SCAN_FOLDER);
            Path file = Path.of(TEST_SCAN_FOLDER + "photo.jpg");
            mockedFiles.when(() -> Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> Files.walk(folder)).thenReturn(Stream.of(folder, file));
            mockedFiles.when(() -> Files.isRegularFile(folder)).thenReturn(false);
            mockedFiles.when(() -> Files.isRegularFile(file)).thenReturn(true);

            assertFalse(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock),
                "A folder with at least one regular file should not be considered empty");
        }
    }

    @Test
    void isScanFolderEmpty_returnsTrueWhenWalkThrowsIOException() throws IOException {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path folder = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> Files.walk(folder)).thenThrow(new IOException("Permission denied"));

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock),
                "An unreadable folder should be treated as empty for safety");
        }
    }

    @Test
    void isScanFolderEmpty_ignoresSubdirectoriesWhenCountingFiles() throws IOException {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path folder = Path.of(TEST_SCAN_FOLDER);
            Path subdir = Path.of(TEST_SCAN_FOLDER + "subdir");
            mockedFiles.when(() -> Files.exists(folder)).thenReturn(true);
            mockedFiles.when(() -> Files.walk(folder)).thenReturn(Stream.of(folder, subdir));
            mockedFiles.when(() -> Files.isRegularFile(folder)).thenReturn(false);
            mockedFiles.when(() -> Files.isRegularFile(subdir)).thenReturn(false);

            assertTrue(serviceImplSpy.isScanFolderEmpty(scanLocationConfigMock),
                "A folder containing only subdirectories should be considered empty");
        }
    }

    // -------------------------------------------------------------------------
    // startCloudCleanup safety guard — via performLocationSync
    // -------------------------------------------------------------------------

    @Test
    void performLocationSync_skipsCleanupAndNotifiesWhenFolderIsEmptyAndGuardNotEnabled() throws CloudBackupException {
        Mockito.when(scanLocationConfigMock.isCleanRemovedFromCloud()).thenReturn(true);
        Mockito.when(scanLocationConfigMock.isDeleteIfEmptyEnabled()).thenReturn(false);
        Mockito.doReturn(true).when(serviceImplSpy).isScanFolderEmpty(scanLocationConfigMock);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path directory = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.walk(directory))
                       .thenReturn(Stream.of(Path.of(TEST_SCAN_FOLDER + "file1.jpg")));
            serviceImplSpy.performLocationSync(scanLocationConfigMock);
        }

        // Cleanup catalog query must NOT have been called (only the backup query ran)
        Mockito.verify(fileCatalogItemRepository, Mockito.times(1))
               .findByParentFolderStartsWith(Mockito.anyString(), Mockito.any());
        // Warning notification must have been sent
        Mockito.verify(notificationService)
               .notifyError(Mockito.anyString(), Mockito.any(ScanLocationConfig.class));
    }

    @Test
    void performLocationSync_proceedsWithCleanupWhenFolderIsEmptyAndGuardIsEnabled() throws CloudBackupException {
        Mockito.when(scanLocationConfigMock.isCleanRemovedFromCloud()).thenReturn(true);
        Mockito.when(scanLocationConfigMock.isDeleteIfEmptyEnabled()).thenReturn(true);
        Mockito.doReturn(true).when(serviceImplSpy).isScanFolderEmpty(scanLocationConfigMock);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path directory = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.walk(directory))
                       .thenReturn(Stream.of(Path.of(TEST_SCAN_FOLDER + "file1.jpg")));
            serviceImplSpy.performLocationSync(scanLocationConfigMock);
        }

        // Both backup and cleanup catalog queries must have run
        Mockito.verify(fileCatalogItemRepository, Mockito.times(2))
               .findByParentFolderStartsWith(Mockito.anyString(), Mockito.any());
        // No error notification for the guard
        Mockito.verify(notificationService, Mockito.never())
               .notifyError(Mockito.anyString(), Mockito.any(ScanLocationConfig.class));
    }

    @Test
    void performLocationSync_proceedsWithCleanupNormallyWhenFolderIsNotEmpty() throws CloudBackupException {
        Mockito.when(scanLocationConfigMock.isCleanRemovedFromCloud()).thenReturn(true);
        Mockito.when(scanLocationConfigMock.isDeleteIfEmptyEnabled()).thenReturn(false);
        Mockito.doReturn(false).when(serviceImplSpy).isScanFolderEmpty(scanLocationConfigMock);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path directory = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.walk(directory))
                       .thenReturn(Stream.of(Path.of(TEST_SCAN_FOLDER + "file1.jpg")));
            serviceImplSpy.performLocationSync(scanLocationConfigMock);
        }

        // Both backup and cleanup catalog queries must have run
        Mockito.verify(fileCatalogItemRepository, Mockito.times(2))
               .findByParentFolderStartsWith(Mockito.anyString(), Mockito.any());
        // No guard notification
        Mockito.verify(notificationService, Mockito.never())
               .notifyError(Mockito.anyString(), Mockito.any(ScanLocationConfig.class));
    }

    @Test
    void performLocationSync_doesNotCheckEmptyGuardWhenCleanupIsDisabled() throws CloudBackupException {
        Mockito.when(scanLocationConfigMock.isCleanRemovedFromCloud()).thenReturn(false);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            Path directory = Path.of(TEST_SCAN_FOLDER);
            mockedFiles.when(() -> Files.walk(directory))
                       .thenReturn(Stream.of(Path.of(TEST_SCAN_FOLDER + "file1.jpg")));
            serviceImplSpy.performLocationSync(scanLocationConfigMock);
        }

        // isScanFolderEmpty must never be called when cleanRemovedFromCloud=false
        Mockito.verify(serviceImplSpy, Mockito.never()).isScanFolderEmpty(Mockito.any());
    }

    // -------------------------------------------------------------------------
    // findPendingDeletion tests
    // -------------------------------------------------------------------------

    private ScanLocationConfig buildLocation(String scanFolder, Integer standardDeleteDaysLimit, Integer archiveDeleteDaysHold) {
        ScanLocationConfig loc = new ScanLocationConfig();
        loc.setScanFolder(scanFolder);
        loc.setStandardDeleteDaysLimit(standardDeleteDaysLimit);
        loc.setArchiveDeleteDaysHold(archiveDeleteDaysHold);
        loc.setCollectionFetchSize(500);
        return loc;
    }

    private Date daysAgo(int days) {
        return Date.from(LocalDate.now().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void findPendingDeletion_noFilter_returnsAllMissingFiles() {
        FileCatalogItem missing1 = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, daysAgo(10), "crc1", Instant.now());
        FileCatalogItem missing2 = new FileCatalogItem("/scan/b.jpg", "b.jpg", "jpg", "/scan", false, 200L, daysAgo(5), "crc2", Instant.now());
        FileCatalogItem existing = new FileCatalogItem("/scan/c.jpg", "c.jpg", "jpg", "/scan", false, 300L, daysAgo(3), "crc3", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(Arrays.asList(missing1, missing2, existing));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 10)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Path.of("/scan/a.jpg"))).thenReturn(true);
            mockedFiles.when(() -> Files.notExists(Path.of("/scan/b.jpg"))).thenReturn(true);
            mockedFiles.when(() -> Files.notExists(Path.of("/scan/c.jpg"))).thenReturn(false);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(2, result.size());
            assertTrue(result.stream().anyMatch(r -> r.catalogItem().fileName().equals("a.jpg")));
            assertTrue(result.stream().anyMatch(r -> r.catalogItem().fileName().equals("b.jpg")));
            Mockito.verify(thumbnailService, Mockito.never()).deleteThumbnail(Mockito.any());
            Mockito.verify(fileCatalogItemRepository, Mockito.never()).delete(Mockito.any());
        }
    }

    @Test
    void findPendingDeletion_fileNameContains_delegatesToRepository() {
        FileCatalogItem item1 = new FileCatalogItem("/scan/photo.jpg", "photo.jpg", "jpg", "/scan", false, 100L, daysAgo(10), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item1));
        Mockito.when(fileCatalogItemRepository.findByFileNameContainsIgnoreCase(Mockito.eq("PHOTO"), Mockito.any()))
               .thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 0)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.of("PHOTO"), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            Mockito.verify(fileCatalogItemRepository)
                   .findByFileNameContainsIgnoreCase(Mockito.eq("PHOTO"), Mockito.any());
            Mockito.verify(fileCatalogItemRepository, Mockito.never())
                   .findAll(Mockito.any(org.springframework.data.domain.Pageable.class));
        }
    }

    @Test
    void findPendingDeletion_fileNameExact_delegatesToRepository() {
        FileCatalogItem item1 = new FileCatalogItem("/scan/photo.jpg", "photo.jpg", "jpg", "/scan", false, 100L, daysAgo(10), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item1));
        Mockito.when(fileCatalogItemRepository.findByFileName(Mockito.eq("photo.jpg"), Mockito.any()))
               .thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 0)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.of("photo.jpg"), Optional.empty());

            assertEquals(1, result.size());
            Mockito.verify(fileCatalogItemRepository)
                   .findByFileName(Mockito.eq("photo.jpg"), Mockito.any());
            Mockito.verify(fileCatalogItemRepository, Mockito.never())
                   .findAll(Mockito.any(org.springframework.data.domain.Pageable.class));
        }
    }

    @Test
    void findPendingDeletion_pathFilter_restrictsToPrefix() {
        FileCatalogItem item1 = new FileCatalogItem("/scan/sub/a.jpg", "a.jpg", "jpg", "/scan/sub", false, 100L, daysAgo(10), "crc1", Instant.now());
        FileCatalogItem item2 = new FileCatalogItem("/scan/sub/b.jpg", "b.jpg", "jpg", "/scan/sub", false, 200L, daysAgo(5), "crc2", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(Arrays.asList(item1, item2));
        Mockito.when(fileCatalogItemRepository.findByParentFolderStartsWith(Mockito.eq("/scan/sub"), Mockito.any())).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 0)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.of("/scan/sub"));

            assertEquals(2, result.size());
            Mockito.verify(fileCatalogItemRepository).findByParentFolderStartsWith(Mockito.eq("/scan/sub"), Mockito.any());
        }
    }

    @Test
    void findPendingDeletion_daysUntilDeletion_computedCorrectly() {
        // archiveDate = 20 days ago, standardDeleteDaysLimit = 30, archiveDeleteDaysHold = 5
        // eligible = archiveDate + 35 days = 15 days from now
        FileCatalogItem item = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, daysAgo(20), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 5)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(15L, result.get(0).daysUntilDeletion());
        }
    }

    @Test
    void findPendingDeletion_archiveDateNull_returnsDaysZero() {
        FileCatalogItem item = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, null, "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 5)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(0L, result.get(0).daysUntilDeletion());
        }
    }

    @Test
    void findPendingDeletion_standardDeleteDaysLimitNull_returnsDaysZero() {
        FileCatalogItem item = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, daysAgo(10), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", null, 5)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(0L, result.get(0).daysUntilDeletion());
        }
    }

    @Test
    void findPendingDeletion_archiveDeleteDaysHoldNull_onlyStandardLimitApplies() {
        // archiveDate = 20 days ago, standardDeleteDaysLimit = 30, archiveDeleteDaysHold = null
        // eligible = archiveDate + 30 days = 10 days from now
        FileCatalogItem item = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, daysAgo(20), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, null)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(10L, result.get(0).daysUntilDeletion());
        }
    }

    @Test
    void findPendingDeletion_negativeDays_returnedAsIs() {
        // archiveDate = 50 days ago, standardDeleteDaysLimit = 30, archiveDeleteDaysHold = 5
        // eligible = archiveDate + 35 days = 15 days AGO → daysUntilDeletion = -15
        FileCatalogItem item = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, daysAgo(50), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 5)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(-15L, result.get(0).daysUntilDeletion());
        }
    }

    @Test
    void findPendingDeletion_noMatchingLocation_returnsDaysZeroAndUnknown() {
        FileCatalogItem item = new FileCatalogItem("/other/a.jpg", "a.jpg", "jpg", "/other", false, 100L, daysAgo(10), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 5)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals(0L, result.get(0).daysUntilDeletion());
            assertEquals("unknown", result.get(0).scanFolder());
        }
    }

    @Test
    void findPendingDeletion_longestPrefixWins() {
        // Two locations: /scan/ and /scan/sub/ — item is under /scan/sub/
        ScanLocationConfig shortLoc = buildLocation("/scan/", 10, 0);
        ScanLocationConfig longLoc = buildLocation("/scan/sub/", 30, 5);

        // archiveDate = 20 days ago; with longLoc: eligible = 20 + 35 = 15 days from now
        FileCatalogItem item = new FileCatalogItem("/scan/sub/a.jpg", "a.jpg", "jpg", "/scan/sub", false, 100L, daysAgo(20), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(Arrays.asList(shortLoc, longLoc));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(Optional.empty(), Optional.empty(), Optional.empty());

            assertEquals(1, result.size());
            assertEquals("/scan/sub/", result.get(0).scanFolder());
            assertEquals(15L, result.get(0).daysUntilDeletion());
        }
    }

    @Test
    void resolveOwningLocation_matchesWhenAbsolutePathUsesBackslashesAndScanFolderUsesThem() {
        // Simulates Windows: absolutePath from MongoDB has backslashes,
        // scanFolder from YAML binding also has backslashes
        ScanLocationConfig loc = buildLocation("C:\\Users\\Ringuerel\\tests", 30, 5);
        List<ScanLocationConfig> locations = List.of(loc);

        ScanLocationConfig result = serviceImplSpy.resolveOwningLocation(
                "C:\\Users\\Ringuerel\\tests\\photo.jpg", locations);

        assertNotNull(result, "Should match despite backslash vs forward-slash differences");
        assertEquals("C:\\Users\\Ringuerel\\tests", result.getScanFolder());
    }

    @Test
    void resolveOwningLocation_matchesWhenMixedSeparators() {
        // absolutePath stored with backslashes, scanFolder configured with forward slashes
        ScanLocationConfig loc = buildLocation("C:/Users/Ringuerel/tests", 30, 5);
        List<ScanLocationConfig> locations = List.of(loc);

        ScanLocationConfig result = serviceImplSpy.resolveOwningLocation(
                "C:\\Users\\Ringuerel\\tests\\photo.jpg", locations);

        assertNotNull(result, "Should match when separators differ between absolutePath and scanFolder");
    }

    @Test
    void findPendingDeletion_fileNameContainsAndPath_delegatesToCombinedRepositoryMethod() {
        FileCatalogItem item = new FileCatalogItem("/scan/sub/photo.jpg", "photo.jpg", "jpg", "/scan/sub", false, 100L, daysAgo(10), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findByFileNameContainsIgnoreCaseAndParentFolderStartsWith(
                Mockito.eq("photo"), Mockito.eq("/scan/sub"), Mockito.any()))
               .thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 0)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(
                    Optional.of("photo"), Optional.empty(), Optional.of("/scan/sub"));

            assertEquals(1, result.size());
            Mockito.verify(fileCatalogItemRepository)
                   .findByFileNameContainsIgnoreCaseAndParentFolderStartsWith(
                           Mockito.eq("photo"), Mockito.eq("/scan/sub"), Mockito.any());
        }
    }

    @Test
    void findPendingDeletion_fileNameExactAndPath_delegatesToCombinedRepositoryMethod() {
        FileCatalogItem item = new FileCatalogItem("/scan/sub/photo.jpg", "photo.jpg", "jpg", "/scan/sub", false, 100L, daysAgo(10), "crc1", Instant.now());

        Page<FileCatalogItem> page = buildSinglePage(List.of(item));
        Mockito.when(fileCatalogItemRepository.findByFileNameAndParentFolderStartsWith(
                Mockito.eq("photo.jpg"), Mockito.eq("/scan/sub"), Mockito.any()))
               .thenReturn(page);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(
                List.of(buildLocation("/scan/", 30, 0)));

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.notExists(Mockito.any(Path.class))).thenReturn(true);

            List<PendingDeletionItem> result = serviceImplSpy.findPendingDeletion(
                    Optional.empty(), Optional.of("photo.jpg"), Optional.of("/scan/sub"));

            assertEquals(1, result.size());
            Mockito.verify(fileCatalogItemRepository)
                   .findByFileNameAndParentFolderStartsWith(
                           Mockito.eq("photo.jpg"), Mockito.eq("/scan/sub"), Mockito.any());
        }
    }

    @Test
    void performCloudBackup_afterUploadCreatesThumbnailAndSavesMetadata() throws Exception {
        FileCatalogServiceImpl service = new FileCatalogServiceImpl(
            fileCatalogItemRepository,
            fileCatalogItemMapper,
            cloudProviderFactory,
            applicationProperties,
            summaryRepository,
            notificationService,
            thumbnailService,
            syncLockManager,
            new SimpleMeterRegistry()
        );
        FileCatalogItem source = new FileCatalogItem("/scan/photo.jpg", "photo.jpg", "jpg", "/scan", false, 100L, null, "crc1", Instant.now());
        FileCatalogItem archived = new FileCatalogItem("/scan/photo.jpg", "photo.jpg", "jpg", "/scan", false, 100L, new Date(), "crc1", Instant.now());
        FileCatalogItem withThumbnail = new FileCatalogItem(
                archived.absolutePath(), archived.fileName(), archived.fileExtension(), archived.parentFolder(),
                archived.isDirectory(), archived.fileSize(), archived.archiveDate(), archived.crc32c(), archived.lastModified(),
                "/thumbs/photo.jpg", "GENERATED", "image/jpeg", Instant.now(), ThumbnailStatus.CREATED.name(), null);

        Mockito.when(fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(source)).thenReturn(archived);
        Mockito.when(fileCatalogItemRepository.save(archived)).thenReturn(archived);
        Mockito.when(thumbnailService.createOrUpdateThumbnail(archived, false)).thenReturn(withThumbnail);

        service.performCloudBackup(source);

        Mockito.verify(cloudProvider).upload(archived);
        Mockito.verify(thumbnailService).createOrUpdateThumbnail(archived, false);
        Mockito.verify(fileCatalogItemRepository).save(archived);
        Mockito.verify(fileCatalogItemRepository).save(withThumbnail);
    }

    @Test
    void rebuildThumbnails_missingOnlyProcessesMissingAndPersistsResult() {
        FileCatalogItem missing = new FileCatalogItem("/scan/a.jpg", "a.jpg", "jpg", "/scan", false, 100L, daysAgo(1), "crc1", Instant.now());
        FileCatalogItem existing = new FileCatalogItem(
                "/scan/b.jpg", "b.jpg", "jpg", "/scan", false, 100L, daysAgo(1), "crc2", Instant.now(),
                "/thumbs/b.jpg", "GENERATED", "image/jpeg", Instant.now(), ThumbnailStatus.CREATED.name(), null);
        FileCatalogItem skipped = new FileCatalogItem(
                "/scan/c.txt", "c.txt", "txt", "/scan", false, 100L, daysAgo(1), "crc3", Instant.now(),
                null, "GENERATED", null, null, ThumbnailStatus.SKIPPED.name(), "Unsupported");
        FileCatalogItem updated = new FileCatalogItem(
                missing.absolutePath(), missing.fileName(), missing.fileExtension(), missing.parentFolder(),
                missing.isDirectory(), missing.fileSize(), missing.archiveDate(), missing.crc32c(), missing.lastModified(),
                "/thumbs/a.jpg", "GENERATED", "image/jpeg", Instant.now(), ThumbnailStatus.CREATED.name(), null);

        Mockito.when(applicationProperties.getThumbnailsConfig()).thenReturn(new ApplicationProperties.ThumbnailsConfig());
        Page<FileCatalogItem> page = buildSinglePage(List.of(missing, existing, skipped));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(thumbnailService.createOrUpdateThumbnail(missing, false)).thenReturn(updated);

        ThumbnailRebuildSummary summary = serviceImplSpy.rebuildThumbnails(
                ThumbnailRebuildMode.MISSING_ONLY, Optional.empty(), Optional.empty(), Optional.of(10));

        assertEquals(ThumbnailRebuildMode.MISSING_ONLY, summary.mode());
        assertEquals(1, summary.processedCount());
        assertEquals(1, summary.createdCount());
        assertEquals(0, summary.skippedCount());
        assertEquals(0, summary.failedCount());
        Mockito.verify(thumbnailService).createOrUpdateThumbnail(missing, false);
        Mockito.verify(thumbnailService, Mockito.never()).createOrUpdateThumbnail(existing, false);
        Mockito.verify(thumbnailService, Mockito.never()).createOrUpdateThumbnail(skipped, false);
        Mockito.verify(fileCatalogItemRepository).save(updated);
    }

    @Test
    void rebuildThumbnails_forceProcessesExistingThumbnails() {
        FileCatalogItem existing = new FileCatalogItem(
                "/scan/b.jpg", "b.jpg", "jpg", "/scan", false, 100L, daysAgo(1), "crc2", Instant.now(),
                "/thumbs/b.jpg", "GENERATED", "image/jpeg", Instant.now(), ThumbnailStatus.CREATED.name(), null);
        FileCatalogItem updated = new FileCatalogItem(
                existing.absolutePath(), existing.fileName(), existing.fileExtension(), existing.parentFolder(),
                existing.isDirectory(), existing.fileSize(), existing.archiveDate(), existing.crc32c(), existing.lastModified(),
                "/thumbs/b-new.jpg", "GENERATED", "image/jpeg", Instant.now(), ThumbnailStatus.CREATED.name(), null);

        Mockito.when(applicationProperties.getThumbnailsConfig()).thenReturn(new ApplicationProperties.ThumbnailsConfig());
        Page<FileCatalogItem> page = buildSinglePage(List.of(existing));
        Mockito.when(fileCatalogItemRepository.findAll(Mockito.any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        Mockito.when(thumbnailService.createOrUpdateThumbnail(existing, true)).thenReturn(updated);

        ThumbnailRebuildSummary summary = serviceImplSpy.rebuildThumbnails(
                ThumbnailRebuildMode.FORCE, Optional.empty(), Optional.empty(), Optional.of(10));

        assertEquals(1, summary.processedCount());
        assertEquals(1, summary.createdCount());
        Mockito.verify(thumbnailService).createOrUpdateThumbnail(existing, true);
        Mockito.verify(fileCatalogItemRepository).save(updated);
    }

    @Test
    void handleFileCatalogItemDelete_deletesThumbnailOnlyWhenCatalogEntryReachesEol() throws Exception {
        FileCatalogServiceImpl service = new FileCatalogServiceImpl(
            fileCatalogItemRepository,
            fileCatalogItemMapper,
            cloudProviderFactory,
            applicationProperties,
            summaryRepository,
            notificationService,
            thumbnailService,
            syncLockManager,
            new SimpleMeterRegistry()
        );
        FileCatalogItem item = new FileCatalogItem(
                "/scan/deleted.jpg", "deleted.jpg", "jpg", "/scan", false, 100L, daysAgo(1), "crc1", Instant.now(),
                "/thumbs/deleted.jpg", "GENERATED", "image/jpeg", Instant.now(), ThumbnailStatus.CREATED.name(), null);
        Mockito.doNothing().when(cloudProvider).delete(item);

        Method deleteMethod = FileCatalogServiceImpl.class.getDeclaredMethod("handleFileCatalogItemDelete", FileCatalogItem.class);
        deleteMethod.setAccessible(true);
        deleteMethod.invoke(service, item);

        Mockito.verify(cloudProvider).delete(item);
        Mockito.verify(thumbnailService).deleteThumbnail(item);
        Mockito.verify(fileCatalogItemRepository).delete(item);
    }

    @SuppressWarnings("unchecked")
    private Page<FileCatalogItem> buildSinglePage(List<FileCatalogItem> items) {
        Page<FileCatalogItem> page = Mockito.mock(Page.class);
        Mockito.when(page.getContent()).thenReturn(items);
        Mockito.when(page.hasNext()).thenReturn(false);
        return page;
    }
}
