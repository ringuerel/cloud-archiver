package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

public interface LocationSyncOperations {

    SyncSummaryItem executeBackup(ScanLocationConfig locationConfig) throws CloudBackupException;

    SyncSummaryItem executeCleanup(ScanLocationConfig locationConfig) throws CloudBackupException;

    void persistSummary(SyncSummaryItem summaryItem, ScanLocationConfig locationConfig);
}
