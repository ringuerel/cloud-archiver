package com.homelab.ringue.cloud.archiver.service;

public interface CloudSyncMetricsService {

    CloudSyncMetrics reset();

    CloudSyncMetrics current();
}
