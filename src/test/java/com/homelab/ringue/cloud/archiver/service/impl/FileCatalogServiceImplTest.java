package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;

public class FileCatalogServiceImplTest {

    @Spy
    @InjectMocks
    private FileCatalogServiceImpl serviceImplSpy;

    @Mock
    private FileCatalogItemRepository repositoryMock;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private ScanLocationConfig scanLocationConfigMock;

    @BeforeEach
    public void setupTests(){
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testApplyFilteringRules() {
        FileCatalogItem fileCatalogItem = new FileCatalogItem("C:\\folder1\\.git", ".git", null, "C:\\folder1", true, null, null);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(Arrays.asList(scanLocationConfigMock));
        Mockito.when(scanLocationConfigMock.getIgnorePatterns()).thenReturn(Arrays.asList("^\\..+"));
        boolean shouldBeIgnored = serviceImplSpy.applyFilteringRules(scanLocationConfigMock,fileCatalogItem);
        assertTrue(shouldBeIgnored);
    }
}
