package com.homelab.ringue.cloud.archiver.service;

import java.util.List;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

public interface FileCatalogService {
    
    List<FileCatalogItem> findByFileNameContains(String fileName);

    void performLocationSync(ScanLocationConfig scanlocationconfig) throws CloudBackupException;

}
