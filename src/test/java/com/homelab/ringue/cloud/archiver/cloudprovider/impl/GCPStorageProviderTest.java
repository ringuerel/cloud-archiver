package com.homelab.ringue.cloud.archiver.cloudprovider.impl;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.api.gax.paging.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GCPStorageProviderTest {
    @Mock
    private ApplicationProperties applicationProperties;
    @Mock
    private Storage storage;
    @Mock
    private Blob blob;

    private GCPStorageProvider gcpStorageProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gcpStorageProvider = spy(new GCPStorageProvider(applicationProperties));
    }

    @Test
    void testDownloadSingleFile() throws IOException {
        String cloudPath = "immich/external/johan/whatsapp/MarthaYSofiConTias.jpeg";
        String localTargetPath = "C:/downloads";
        doReturn(storage).when(gcpStorageProvider).getConfiguredStorage(any());
        when(applicationProperties.getCloudProviderConfig()).thenReturn(mock(ApplicationProperties.CloudProviderConfig.class));
        when(applicationProperties.getCloudProviderConfig().getBucketName()).thenReturn("bucket");
        when(applicationProperties.getCloudProviderConfig().getProjectId()).thenReturn("project");
        when(storage.get("bucket", cloudPath)).thenReturn(blob);
        when(blob.getName()).thenReturn(cloudPath);
        doNothing().when(blob).downloadTo(any(Path.class));

        gcpStorageProvider.download(cloudPath, localTargetPath);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(blob).downloadTo(pathCaptor.capture());
        Path capturedPath = pathCaptor.getValue();
        Path expectedPath = java.nio.file.Paths.get(localTargetPath, cloudPath);
        assertEquals(expectedPath, capturedPath);
    }

    @Test
    void testDownloadFolderRecursively() throws IOException {
        String cloudPath = "immich/library/admin/2008/2008-12-31/";
        String localTargetPath = "C:/downloads";
        doReturn(storage).when(gcpStorageProvider).getConfiguredStorage(any());
        when(applicationProperties.getCloudProviderConfig()).thenReturn(mock(ApplicationProperties.CloudProviderConfig.class));
        when(applicationProperties.getCloudProviderConfig().getBucketName()).thenReturn("bucket");
        when(applicationProperties.getCloudProviderConfig().getProjectId()).thenReturn("project");
        Blob fileBlob = mock(Blob.class);
        when(fileBlob.isDirectory()).thenReturn(false);
        when(fileBlob.getName()).thenReturn("immich/library/admin/2008/2008-12-31/AbuelaBernardina.jpg");
        doNothing().when(fileBlob).downloadTo(any(Path.class));
        Iterable<Blob> blobs = java.util.List.of(fileBlob);
        Storage.BlobListOption prefixOption = Storage.BlobListOption.prefix(cloudPath);
        Page<Blob> pageMock = mock(Page.class);
        when(storage.list(eq("bucket"), any(Storage.BlobListOption.class))).thenReturn(pageMock);
        when(pageMock.iterateAll()).thenReturn(blobs);

        gcpStorageProvider.download(cloudPath, localTargetPath);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(fileBlob).downloadTo(pathCaptor.capture());
        Path captured = pathCaptor.getValue();
        Path expected = java.nio.file.Paths.get(localTargetPath, "immich/library/admin/2008/2008-12-31/AbuelaBernardina.jpg");
        assertEquals(expected, captured);
    }

    @Test
    void testBuildLocalFilePath() {
        String localRoot = "C:/downloads";
        String cloudNameWithBackslashes = "immich\\external\\johan\\whatsapp\\MarthaYSofiConTias.jpeg";
        GCPStorageProvider provider = new GCPStorageProvider(applicationProperties);
        java.nio.file.Path expected = java.nio.file.Paths.get(localRoot, "immich/external/johan/whatsapp/MarthaYSofiConTias.jpeg");
        java.nio.file.Path result = provider.buildLocalFilePath(localRoot, cloudNameWithBackslashes);
        assertEquals(expected, result);
    }

    @Test
    void testDownloadFileNotFoundThrows() throws IOException {
        String cloudPath = "immich/library/admin/2008/2008-12-31/NotFound.jpg";
        String localTargetPath = "C:/downloads";
        doReturn(storage).when(gcpStorageProvider).getConfiguredStorage(any());
        when(applicationProperties.getCloudProviderConfig()).thenReturn(mock(ApplicationProperties.CloudProviderConfig.class));
        when(applicationProperties.getCloudProviderConfig().getBucketName()).thenReturn("bucket");
        when(applicationProperties.getCloudProviderConfig().getProjectId()).thenReturn("project");
        when(storage.get("bucket", cloudPath)).thenReturn(null);

        assertThrows(IOException.class, () -> gcpStorageProvider.download(cloudPath, localTargetPath));
    }
}
