package com.homelab.ringue.cloud.archiver.service;

import java.time.Duration;

public interface CloudSyncMetricsService {

    CloudSyncMetrics current();

    void recordThumbnailCreated(String mediaType, Duration duration);

    void recordThumbnailFailed(String mediaType, Duration duration);

    void recordThumbnailSkipped(String mediaType);
}
