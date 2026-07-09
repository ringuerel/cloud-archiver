package com.homelab.ringue.cloud.archiver.domain.gallery;

public record RestoreResponse(
    String status,
    String restoreJobId,
    String absolutePath,
    String originalUrl,
    String downloadRoot
) {}
