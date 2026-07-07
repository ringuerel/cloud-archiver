package com.homelab.ringue.cloud.archiver.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;

public record CloudSyncMetrics(
        Counter filesUploadedCounter,
        Counter filesDeletedCounter,
        Timer uploadTimer,
        Timer deleteTimer,
        Timer scanDurationTimer,
        Gauge filesInCatalogGauge,
        Counter gcpDownloadsCounter,
        DistributionSummary gcpUploadBytesSummary,
        DistributionSummary gcpDownloadBytesSummary) {
}
