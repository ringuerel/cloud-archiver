package com.homelab.ringue.cloud.archiver.service.impl;

import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetrics;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetricsService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class CloudSyncMetricsServiceImpl implements CloudSyncMetricsService {

    private final CloudSyncMetrics metrics;

    public CloudSyncMetricsServiceImpl(MeterRegistry meterRegistry, FileCatalogItemRepository fileCatalogItemRepository) {
        this.metrics = new CloudSyncMetrics(
                Counter.builder("cloud_archiver_files_uploaded_total")
                        .description("Total number of files successfully uploaded to the cloud")
                        .register(meterRegistry),
                Counter.builder("cloud_archiver_files_deleted_total")
                        .description("Total number of files deleted from the cloud")
                        .register(meterRegistry),
                Timer.builder("cloud_archiver_upload_duration_seconds")
                        .description("Time taken for file upload operations")
                        .register(meterRegistry),
                Timer.builder("cloud_archiver_delete_duration_seconds")
                        .description("Time taken for file deletion operations")
                        .register(meterRegistry),
                Timer.builder("cloud_archiver_scan_duration_seconds")
                        .description("Duration of the folder scanning process")
                        .register(meterRegistry),
                Gauge.builder("cloud_archiver_files_in_catalog", fileCatalogItemRepository,
                                repo -> (double) repo.count())
                        .description("Current number of files cataloged in the database")
                        .register(meterRegistry),
                Counter.builder("cloud_archiver_gcp_downloads_total")
                        .description("Total number of GCP download operations")
                        .register(meterRegistry),
                DistributionSummary.builder("cloud_archiver_gcp_upload_bytes")
                        .description("Total bytes uploaded to GCP")
                        .baseUnit("bytes")
                        .register(meterRegistry),
                DistributionSummary.builder("cloud_archiver_gcp_download_bytes")
                        .description("Total bytes downloaded from GCP")
                        .baseUnit("bytes")
                        .register(meterRegistry));
    }

    @Override
    public CloudSyncMetrics current() {
        return metrics;
    }
}
