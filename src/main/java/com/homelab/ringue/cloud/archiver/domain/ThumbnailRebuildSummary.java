package com.homelab.ringue.cloud.archiver.domain;

public record ThumbnailRebuildSummary(
    ThumbnailRebuildMode mode,
    int processedCount,
    int createdCount,
    int skippedCount,
    int failedCount
) {}
