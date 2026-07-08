package com.homelab.ringue.cloud.archiver.domain.gallery;

import java.time.Instant;

public record RestoreJob(
    String jobId,
    String absolutePath,
    Instant startedAt,
    RestoreJobStatus status
) {}
