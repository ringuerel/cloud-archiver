package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
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

public class FileCatalogServiceImplTest {

    private static final String TEST_SCAN_FOLDER = "/test/scan/folder/";
    private static final String CRC32C = "MOCKCRC32C";

    @Spy
    @InjectMocks
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
    private CloudProvider cloudProvider;

    @BeforeEach
    public void setupTests(){
        MockitoAnnotations.openMocks(this);
        Mockito.when(scanLocationConfigMock.getScanFolder()).thenReturn(TEST_SCAN_FOLDER);
        Mockito.when(applicationProperties.getCloudProviderConfig()).thenReturn(cloudProviderConfig);
        Mockito.when(cloudProviderConfig.getType()).thenReturn(CloudProviders.NO_PROVIDER);
        Mockito.when(cloudProviderFactory.getCloudProvider(Mockito.any())).thenReturn(cloudProvider);
        Mockito.when(fileCatalogItemRepository.findByParentFolder(Mockito.anyString(),Mockito.any())).thenReturn(fileCatalogPageMock);
    }


    @Test
    void testApplyFilteringRules() {
        FileCatalogItem fileCatalogItem = new FileCatalogItem("C:\\folder1\\.git", ".git", null, "C:\\folder1", true, null, null,null);
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
        Mockito.verify(fileCatalogItemRepository,Mockito.times(cleanRemovedFromCloud?2:1)).findByParentFolder(Mockito.anyString(),Mockito.any());
        Mockito.verify(serviceImplSpy).processFileStreamForBackup(Mockito.any(),Mockito.any(),Mockito.any());
    }


    @Test
    void processFileStreamForBackupIgnoresBackedUpItems() throws IOException{
        FileCatalogItem someFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 50L, null,CRC32C);
        FileCatalogItem otherFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/otherFile.jpg", "someFile.jpg", "jpg", TEST_SCAN_FOLDER, false, 70L, null,CRC32C);
        FileCatalogItem someMovFile = new FileCatalogItem(TEST_SCAN_FOLDER+"/someMovFile.mov", "someMovFile.mov", "mov", TEST_SCAN_FOLDER, false, 250L, null,CRC32C);
        FileCatalogItem someFolder = new FileCatalogItem(TEST_SCAN_FOLDER+"/someFolder", "someMovFile", null, TEST_SCAN_FOLDER, true, 0L, null,CRC32C);
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
        Mockito.doReturn(CRC32C).when(serviceImplSpy).getCrC32C(Mockito.anyString());
        Mockito.doReturn(someFile).when(serviceImplSpy).getCrC32CPopulatedItem(someFile);
        Mockito.doReturn(otherFile).when(serviceImplSpy).getCrC32CPopulatedItem(otherFile);
        Mockito.doReturn(someMovFile).when(serviceImplSpy).getCrC32CPopulatedItem(someMovFile);
        Mockito.doReturn(someFolder).when(serviceImplSpy).getCrC32CPopulatedItem(someFolder);
        Map<String, FileCatalogItem> backedUpItems = new HashMap<>();
        backedUpItems.put(someMovFile.absolutePath(), someMovFile);
        serviceImplSpy.processFileStreamForBackup(scanLocationConfigMock, backedUpItems, filesStream);
        Mockito.verify(serviceImplSpy,Mockito.times(2)).performCloudBackup(Mockito.any());
    }


    private Stream<Path> prepareFilesStream(List<String> filesPaths) {
        return filesPaths.stream().map(Path::of);
    }
}
