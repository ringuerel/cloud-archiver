package com.homelab.ringue.cloud.archiver.gallery;

import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJob;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJobStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for tracking active restore jobs, keyed by absolutePath.
 * Thread-safe via ConcurrentHashMap.
 */
@Component
public class RestoreJobRegistry {

    private final ConcurrentHashMap<String, RestoreJob> activeJobs = new ConcurrentHashMap<>();

    /**
     * Returns the RestoreJob for the given absolutePath, if one exists.
     */
    public Optional<RestoreJob> findJob(String absolutePath) {
        return Optional.ofNullable(activeJobs.get(absolutePath));
    }

    /**
     * Creates a new RestoreJob with RUNNING status, stores it, and returns it.
     */
    public RestoreJob registerJob(String absolutePath) {
        RestoreJob job = new RestoreJob(
            UUID.randomUUID().toString(),
            absolutePath,
            Instant.now(),
            RestoreJobStatus.RUNNING
        );
        activeJobs.put(absolutePath, job);
        return job;
    }

    /**
     * Updates the job for the given absolutePath to COMPLETED status.
     * No-op if no job exists for that path.
     */
    public void completeJob(String absolutePath) {
        activeJobs.computeIfPresent(absolutePath, (key, existing) ->
            new RestoreJob(existing.jobId(), existing.absolutePath(), existing.startedAt(), RestoreJobStatus.COMPLETED)
        );
    }

    /**
     * Updates the job for the given absolutePath to FAILED status.
     * No-op if no job exists for that path.
     */
    public void failJob(String absolutePath) {
        activeJobs.computeIfPresent(absolutePath, (key, existing) ->
            new RestoreJob(existing.jobId(), existing.absolutePath(), existing.startedAt(), RestoreJobStatus.FAILED)
        );
    }
}
