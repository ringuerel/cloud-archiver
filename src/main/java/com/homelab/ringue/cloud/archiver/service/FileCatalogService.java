package com.homelab.ringue.cloud.archiver.service;

import java.util.List;
import java.util.Optional;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.PendingDeletionItem;

public interface FileCatalogService {

    List<FileCatalogItem> findByFileNameContains(String fileName);

    List<FileCatalogItem> findByFileNameSimilar(String fileName);

    List<FileCatalogItem> findByArchiveDateBetweenAndAbsolutePathStartsWith(String startDate, String endDate,
            Optional<String> path);

    /**
     * Returns catalog items that no longer exist on disk, enriched with days-until-deletion
     * computed from the owning ScanLocationConfig's delete policy.
     */
    List<PendingDeletionItem> findPendingDeletion(Optional<String> fileNameContains, Optional<String> fileNameExact,
            Optional<String> path);

    /**
     * Downloads a file or folder from the cloud provider to the local downloadRoot.
     *
     * @param cloudPath the path in the cloud provider (e.g. /, /folder/, /file.jpg)
     * @return true if the download was successful, false otherwise
     */
    boolean downloadFromCloud(String cloudPath);
}
