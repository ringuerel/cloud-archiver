package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.repository.SyncSummaryRepository;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
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
        Mockito.doNothing().when(serviceImplSpy).performCloudBackup(Mockito.any(ScanLocationConfig.class), Mockito.any());
        Mockito.doReturn(someFile).when(serviceImplSpy).getCrC32CPopulatedItem(someFile);
        Mockito.doReturn(otherFile).when(serviceImplSpy).getCrC32CPopulatedItem(otherFile);
        Mockito.doReturn(someMovFile).when(serviceImplSpy).getCrC32CPopulatedItem(someMovFile);
        Map<String, FileCatalogItem> backedUpItems = new HashMap<>();
        backedUpItems.put(someMovFile.absolutePath(), someMovFile);
        serviceImplSpy.processFileStreamForBackup(scanLocationConfigMock, backedUpItems, filesStream);
        Mockito.verify(serviceImplSpy,Mockito.times(2)).performCloudBackup(Mockito.any(ScanLocationConfig.class), Mockito.any());
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
        Mockito.doNothing().when(serviceImplSpy).performCloudBackup(Mockito.any(ScanLocationConfig.class), Mockito.any());
        Map<String, FileCatalogItem> backedUpItems = new HashMap<>();
        backedUpItems.put(updatedFile.absolutePath(), updatedFile);
        backedUpItems.put(someFile.absolutePath(), someFile); 
        Mockito.doReturn(null).when(serviceImplSpy).getFileToProcessIfAny(backedUpItems, updatedFile);
        serviceImplSpy.processFileStreamForBackup(scanLocationConfigMock, backedUpItems, filesStream);
        Mockito.verify(serviceImplSpy).performCloudBackup(Mockito.any(ScanLocationConfig.class), Mockito.any());
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
            getMetricField(target, "gcpDownloadBytesSummary"),
            getMetricField(target, "gcpDeleteBytesSummary")
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
        DistributionSummary gcpDownloadBytesSummary,
        DistributionSummary gcpDeleteBytesSummary
    ) {}

    private Stream<Path> prepareFilesStream(List<String> filesPaths) {
        return filesPaths.stream().map(Path::of);
    }
}
