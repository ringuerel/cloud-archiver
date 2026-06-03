package com.homelab.ringue.cloud.archiver.service.impl;

import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetricsService;
import com.homelab.ringue.cloud.archiver.service.CloudSyncOrchestrator;
import com.homelab.ringue.cloud.archiver.service.SyncFacadeService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SyncFacadeServiceImpl implements SyncFacadeService {

    private final CloudSyncOrchestrator cloudSyncOrchestrator;
    private final CloudSyncMetricsService cloudSyncMetricsService;

    public SyncFacadeServiceImpl(
            CloudSyncOrchestrator cloudSyncOrchestrator,
            CloudSyncMetricsService cloudSyncMetricsService) {
        this.cloudSyncOrchestrator = cloudSyncOrchestrator;
        this.cloudSyncMetricsService = cloudSyncMetricsService;
    }

    @Override
    public void performLocationSync(ScanLocationConfig locationConfig) throws CloudBackupException {
        cloudSyncOrchestrator.performLocationSync(locationConfig);
    }

    @Override
    public boolean startAllLocationSyncs() {
        cloudSyncMetricsService.reset();
        return cloudSyncOrchestrator.startAllLocationSyncs();
    }
}
