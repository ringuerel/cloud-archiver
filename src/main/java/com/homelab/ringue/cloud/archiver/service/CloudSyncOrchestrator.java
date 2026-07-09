package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

public interface CloudSyncOrchestrator {

    void performLocationSync(ScanLocationConfig locationConfig) throws CloudBackupException;

    boolean startAllLocationSyncs();
}
