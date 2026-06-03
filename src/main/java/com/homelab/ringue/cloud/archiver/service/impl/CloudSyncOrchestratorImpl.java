package com.homelab.ringue.cloud.archiver.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncOrchestrator;
import com.homelab.ringue.cloud.archiver.service.LocationSyncOperations;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
import com.homelab.ringue.cloud.archiver.service.SyncLockManager;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CloudSyncOrchestratorImpl implements CloudSyncOrchestrator {

    private final ApplicationProperties applicationProperties;
    private final LocationSyncOperations locationSyncOperations;
    private final NotificationService notificationService;
    private final SyncLockManager syncLockManager;
    private final Timer scanDurationTimer;

    public CloudSyncOrchestratorImpl(
            ApplicationProperties applicationProperties,
            LocationSyncOperations locationSyncOperations,
            NotificationService notificationService,
            SyncLockManager syncLockManager,
            MeterRegistry meterRegistry) {
        this.applicationProperties = applicationProperties;
        this.locationSyncOperations = locationSyncOperations;
        this.notificationService = notificationService;
        this.syncLockManager = syncLockManager;
        this.scanDurationTimer = Timer.builder("cloud_archiver_scan_duration_seconds")
                .description("Duration of the folder scanning process")
                .register(meterRegistry);
    }

    @Override
    public void performLocationSync(ScanLocationConfig locationConfig) throws CloudBackupException {
        String location = describeLocation(locationConfig);
        String runId = Optional.ofNullable(CloudSyncContext.getRunId().orElse(null)).orElseGet(CloudSyncContext::newRunId);
        log.info("Starting sync orchestration for location={} runId={}", location, runId);

        try (CloudSyncContext.Scope ignored = CloudSyncContext.open(runId, locationConfig, "backup")) {
            Instant start = Instant.now();
            notificationService.notifyInfoMessage("Started backup process", locationConfig);
            SyncSummaryItem backupSummary = locationSyncOperations.executeBackup(locationConfig);
            SyncSummaryItem cleanupSummary = shouldRunCleanup(locationConfig)
                    ? runCleanup(locationConfig)
                    : emptySummary();
            SyncSummaryItem locationSummary = mergeSummaries(backupSummary, cleanupSummary);
            locationSyncOperations.persistSummary(locationSummary, locationConfig);
            log.info(
                    "Completed sync orchestration for location={} uploads={} uploadBytes={} deletes={} deleteBytes={}",
                    location,
                    locationSummary.uploadCount(),
                    locationSummary.uploadSize(),
                    locationSummary.deleteCount(),
                    locationSummary.deleteSize());
            scanDurationTimer.record(Duration.between(start, Instant.now()));
        }
    }

    @Override
    public boolean startAllLocationSyncs() {
        long timeoutSeconds = applicationProperties.getSyncLockTimeoutSeconds();
        if (!syncLockManager.acquireLock(timeoutSeconds)) {
            log.info("Skipping sync process because lock acquisition failed for timeout={}s.", timeoutSeconds);
            return false;
        }

        String runId = CloudSyncContext.newRunId();
        try (CloudSyncContext.Scope ignored = CloudSyncContext.open(runId, null, "orchestration")) {
            log.info("Starting sync orchestration runId={}", runId);
            List<ScanLocationConfig> scanLocations = Optional.ofNullable(applicationProperties.getScanFolders())
                    .orElse(List.of());
            if (scanLocations.isEmpty()) {
                log.warn("No scan folders configured. Skipping sync runId={}", runId);
                return true;
            }

            for (ScanLocationConfig locationConfig : scanLocations) {
                String location = describeLocation(locationConfig);
                try {
                    log.info("Dispatching sync for location={} runId={}", location, runId);
                    performLocationSync(locationConfig);
                } catch (CloudBackupException e) {
                    log.error("Location sync failed for location={} runId={}", location, runId, e);
                    notificationService.notifyError(
                            "Error during sync for location " + location + ": " + e.getMessage(),
                            locationConfig);
                }
            }
            log.info("Completed sync orchestration runId={}", runId);
            return true;
        } finally {
            syncLockManager.releaseLock();
            CloudSyncContext.clear();
        }
    }

    private SyncSummaryItem runCleanup(ScanLocationConfig locationConfig) throws CloudBackupException {
        CloudSyncContext.updatePhase("cleanup");
        notificationService.notifyInfoMessage("Started cleanup process", locationConfig);
        return locationSyncOperations.executeCleanup(locationConfig);
    }

    private boolean shouldRunCleanup(ScanLocationConfig locationConfig) {
        return locationConfig != null && locationConfig.isCleanRemovedFromCloud();
    }

    private SyncSummaryItem mergeSummaries(SyncSummaryItem backupSummary, SyncSummaryItem cleanupSummary) {
        return new SyncSummaryItem(
                null,
                backupSummary.uploadCount(),
                backupSummary.uploadSize(),
                cleanupSummary.deleteCount(),
                cleanupSummary.deleteSize(),
                Instant.now());
    }

    private SyncSummaryItem emptySummary() {
        return new SyncSummaryItem(null, 0, 0L, 0, 0L, Instant.now());
    }

    private String describeLocation(ScanLocationConfig locationConfig) {
        if (locationConfig == null || locationConfig.getScanFolder() == null || locationConfig.getScanFolder().isBlank()) {
            return "<unconfigured>";
        }
        return locationConfig.getScanFolder();
    }
}
