package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetrics;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetricsService;
import com.homelab.ringue.cloud.archiver.service.LocationSyncOperations;
import com.homelab.ringue.cloud.archiver.service.NotificationService;
import com.homelab.ringue.cloud.archiver.service.SyncLockManager;

@ExtendWith(MockitoExtension.class)
class CloudSyncOrchestratorImplTest {

    @Mock
    private ApplicationProperties applicationProperties;
    @Mock
    private LocationSyncOperations locationSyncOperations;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SyncLockManager syncLockManager;
    @Mock
    private CloudSyncMetricsService cloudSyncMetricsService;
    @Mock
    private CloudSyncMetrics cloudSyncMetrics;

    private CloudSyncOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CloudSyncOrchestratorImpl(
                applicationProperties,
                locationSyncOperations,
                notificationService,
                syncLockManager,
                cloudSyncMetricsService);
    }

    private void stubScanDurationTimer() {
        Mockito.when(cloudSyncMetricsService.current()).thenReturn(cloudSyncMetrics);
        Mockito.when(cloudSyncMetrics.scanDurationTimer())
                .thenReturn(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
                        .timer("cloud_archiver_scan_duration_seconds"));
    }

    @AfterEach
    void clearMdc() {
        CloudSyncContext.clear();
    }

    @Test
    void startAllLocationSyncsReturnsFalseWhenLockBusy() {
        Mockito.when(applicationProperties.getSyncLockTimeoutSeconds()).thenReturn(42L);
        Mockito.when(syncLockManager.acquireLock(42L)).thenReturn(false);

        boolean started = orchestrator.startAllLocationSyncs();

        assertFalse(started);
        Mockito.verify(syncLockManager, Mockito.never()).releaseLock();
        Mockito.verifyNoInteractions(locationSyncOperations);
        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
    }

    @Test
    void startAllLocationSyncsRunsEachLocationAndReleasesLock() throws Exception {
        stubScanDurationTimer();
        ScanLocationConfig first = scanLocation("/scan/a", true);
        ScanLocationConfig second = scanLocation("/scan/b", false);
        Mockito.when(applicationProperties.getSyncLockTimeoutSeconds()).thenReturn(15L);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(List.of(first, second));
        Mockito.when(syncLockManager.acquireLock(15L)).thenReturn(true);
        Mockito.when(locationSyncOperations.executeBackup(Mockito.any()))
                .thenReturn(summary(2, 200L, 0, 0L));
        Mockito.when(locationSyncOperations.executeCleanup(first))
                .thenReturn(summary(0, 0L, 1, 30L));

        boolean started = orchestrator.startAllLocationSyncs();

        assertTrue(started);
        Mockito.verify(locationSyncOperations).executeBackup(first);
        Mockito.verify(locationSyncOperations).executeBackup(second);
        Mockito.verify(locationSyncOperations).executeCleanup(first);
        Mockito.verify(locationSyncOperations, Mockito.never()).executeCleanup(second);
        Mockito.verify(locationSyncOperations, Mockito.times(2))
                .persistSummary(Mockito.any(SyncSummaryItem.class), Mockito.any(ScanLocationConfig.class));
        Mockito.verify(syncLockManager).releaseLock();
        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    @Test
    void startAllLocationSyncsContinuesAfterLocationFailureAndNotifies() throws Exception {
        stubScanDurationTimer();
        ScanLocationConfig broken = scanLocation("/scan/broken", false);
        ScanLocationConfig healthy = scanLocation("/scan/healthy", false);
        Mockito.when(applicationProperties.getSyncLockTimeoutSeconds()).thenReturn(9L);
        Mockito.when(applicationProperties.getScanFolders()).thenReturn(List.of(broken, healthy));
        Mockito.when(syncLockManager.acquireLock(9L)).thenReturn(true);
        Mockito.when(locationSyncOperations.executeBackup(broken))
                .thenThrow(new CloudBackupException("boom", new RuntimeException("boom")));
        Mockito.when(locationSyncOperations.executeBackup(healthy))
                .thenReturn(summary(1, 10L, 0, 0L));

        boolean started = orchestrator.startAllLocationSyncs();

        assertTrue(started);
        Mockito.verify(locationSyncOperations).executeBackup(broken);
        Mockito.verify(locationSyncOperations).executeBackup(healthy);
        Mockito.verify(notificationService).notifyError(Mockito.contains("/scan/broken"), Mockito.eq(broken));
        Mockito.verify(locationSyncOperations).persistSummary(Mockito.any(SyncSummaryItem.class), Mockito.eq(healthy));
        Mockito.verify(syncLockManager).releaseLock();
        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
    }

    @Test
    void performLocationSyncPersistsMergedSummaryAndClearsMdc() throws Exception {
        stubScanDurationTimer();
        ScanLocationConfig location = scanLocation("/scan/photos", true);
        Mockito.when(locationSyncOperations.executeBackup(location))
                .thenReturn(summary(3, 300L, 0, 0L));
        Mockito.when(locationSyncOperations.executeCleanup(location))
                .thenReturn(summary(0, 0L, 2, 20L));

        orchestrator.performLocationSync(location);

        Mockito.verify(notificationService).notifyInfoMessage("Started backup process", location);
        Mockito.verify(notificationService).notifyInfoMessage("Started cleanup process", location);
        Mockito.verify(locationSyncOperations).persistSummary(Mockito.argThat(summary ->
                summary.uploadCount() == 3
                        && summary.uploadSize() == 300L
                        && summary.deleteCount() == 2
                        && summary.deleteSize() == 20L), Mockito.eq(location));
        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    @Test
    void performLocationSyncSkipsCleanupWhenDisabled() throws Exception {
        stubScanDurationTimer();
        ScanLocationConfig location = scanLocation("/scan/videos", false);
        Mockito.when(locationSyncOperations.executeBackup(location))
                .thenReturn(summary(4, 400L, 0, 0L));

        orchestrator.performLocationSync(location);

        Mockito.verify(locationSyncOperations, Mockito.never()).executeCleanup(Mockito.any());
        Mockito.verify(locationSyncOperations).persistSummary(Mockito.argThat(summary ->
                summary.uploadCount() == 4
                        && summary.deleteCount() == 0), Mockito.eq(location));
    }

    @Test
    void performLocationSyncClearsMdcWhenBackupFails() throws Exception {
        ScanLocationConfig location = scanLocation("/scan/failure", false);
        Mockito.when(locationSyncOperations.executeBackup(location))
                .thenThrow(new CloudBackupException("backup failed", new RuntimeException("backup failed")));

        assertThrows(CloudBackupException.class, () -> orchestrator.performLocationSync(location));

        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    private ScanLocationConfig scanLocation(String folder, boolean cleanupEnabled) {
        ScanLocationConfig config = new ScanLocationConfig();
        config.setScanFolder(folder);
        config.setCleanRemovedFromCloud(cleanupEnabled);
        return config;
    }

    private SyncSummaryItem summary(int uploads, long uploadBytes, int deletes, long deleteBytes) {
        return new SyncSummaryItem(null, uploads, uploadBytes, deletes, deleteBytes, Instant.now());
    }
}
