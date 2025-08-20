package com.homelab.ringue.cloud.archiver.service;

import java.util.List;
import java.util.Optional;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

public interface FileCatalogService {
    
    List<FileCatalogItem> findByFileNameContains(String fileName);
    List<FileCatalogItem> findByFileNameSimilar(String fileName);
    List<FileCatalogItem> findByArchiveDateBetweenAndAbsolutePathStartsWith(String startDate, String endDate, Optional<String> path);

    void performLocationSync(ScanLocationConfig scanlocationconfig) throws CloudBackupException;

}
