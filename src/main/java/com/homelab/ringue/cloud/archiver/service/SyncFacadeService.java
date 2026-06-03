package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

public interface SyncFacadeService {

    /**
     * Triggers a sync for a single configured scan location.
     */
    void performLocationSync(ScanLocationConfig locationConfig) throws CloudBackupException;

    /**
     * Initiates the synchronization process for all configured scan locations,
     * ensuring only one sync runs at a time via a lock.
     *
     * @return true if the sync started, false if skipped due to an active lock
     */
    boolean startAllLocationSyncs();
}
