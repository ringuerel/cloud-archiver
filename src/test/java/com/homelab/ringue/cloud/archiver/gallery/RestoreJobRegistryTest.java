package com.homelab.ringue.cloud.archiver.gallery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJob;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJobStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestoreJobRegistryTest {

    private final RestoreJobRegistry registry = new RestoreJobRegistry();

    @Test
    void registerJob_createsRunningJobWithUuid() {
        RestoreJob job = registry.registerJob("/media/photo.jpg");

        assertNotNull(job.jobId());
        assertDoesNotThrow(() -> UUID.fromString(job.jobId()));
        assertEquals("/media/photo.jpg", job.absolutePath());
        assertNotNull(job.startedAt());
        assertEquals(RestoreJobStatus.RUNNING, job.status());
    }

    @Test
    void findJob_forUnknownPath_returnsEmpty() {
        Optional<RestoreJob> job = registry.findJob("/media/missing.jpg");

        assertFalse(job.isPresent());
    }

    @Test
    void findJob_afterRegistration_returnsJob() {
        RestoreJob registered = registry.registerJob("/media/photo.jpg");

        Optional<RestoreJob> found = registry.findJob("/media/photo.jpg");

        assertTrue(found.isPresent());
        assertEquals(registered, found.get());
    }

    @Test
    void completeJob_updatesStatusToCompleted() {
        RestoreJob registered = registry.registerJob("/media/photo.jpg");

        registry.completeJob("/media/photo.jpg");

        RestoreJob completed = registry.findJob("/media/photo.jpg").orElseThrow();
        assertEquals(registered.jobId(), completed.jobId());
        assertEquals(registered.startedAt(), completed.startedAt());
        assertEquals(RestoreJobStatus.COMPLETED, completed.status());
    }

    @Test
    void failJob_updatesStatusToFailed() {
        RestoreJob registered = registry.registerJob("/media/photo.jpg");

        registry.failJob("/media/photo.jpg");

        RestoreJob failed = registry.findJob("/media/photo.jpg").orElseThrow();
        assertEquals(registered.jobId(), failed.jobId());
        assertEquals(registered.startedAt(), failed.startedAt());
        assertEquals(RestoreJobStatus.FAILED, failed.status());
    }
}
