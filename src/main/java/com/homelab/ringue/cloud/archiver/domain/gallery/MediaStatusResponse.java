package com.homelab.ringue.cloud.archiver.domain.gallery;

import java.time.Instant;

public record MediaStatusResponse(
    MediaStatusResponse.ThumbnailStatus thumbnail,
    MediaStatusResponse.OriginalStatus original,
    MediaStatusResponse.RestoreStatus restore
) {
    public record ThumbnailStatus(
        boolean available,
        String url,
        String contentType,
        String status,
        String error,
        Instant createdAt
    ) {}

    public record OriginalStatus(
        boolean available,
        String url,
        String contentType,
        Long fileSize,
        Instant lastModified
    ) {}

    public record RestoreStatus(
        boolean available,
        boolean inProgress,
        String jobId
    ) {}
}
