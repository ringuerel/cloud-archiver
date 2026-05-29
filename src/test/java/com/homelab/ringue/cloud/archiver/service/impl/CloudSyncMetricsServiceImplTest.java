package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CloudSyncMetricsServiceImplTest {

    @Test
    void resetReRegistersMetricsAndSwapsCurrentReference() {
        SimpleMeterRegistry meterRegistry = Mockito.spy(new SimpleMeterRegistry());
        FileCatalogItemRepository repository = Mockito.mock(FileCatalogItemRepository.class);
        Mockito.when(repository.count()).thenReturn(3L);

        CloudSyncMetricsServiceImpl service = new CloudSyncMetricsServiceImpl(meterRegistry, repository);

        CloudSyncMetrics beforeReset = service.current();
        beforeReset.filesUploadedCounter().increment();
        beforeReset.gcpUploadBytesSummary().record(42L);

        CloudSyncMetrics afterReset = service.reset();

        assertNotNull(afterReset);
        assertTrue(afterReset != beforeReset);
        assertEquals(0, meterRegistry.getMeters().size());
        assertNull(meterRegistry.find("cloud_archiver_files_uploaded_total").counter());
        assertNull(meterRegistry.find("cloud_archiver_gcp_upload_bytes").summary());
        Mockito.verify(meterRegistry, Mockito.atLeast(9)).remove(Mockito.any(io.micrometer.core.instrument.Meter.class));
    }

    @Test
    void currentReturnsRegisteredMetricsBeforeReset() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileCatalogItemRepository repository = Mockito.mock(FileCatalogItemRepository.class);
        Mockito.when(repository.count()).thenReturn(1L);

        CloudSyncMetricsServiceImpl service = new CloudSyncMetricsServiceImpl(meterRegistry, repository);

        CloudSyncMetrics current = service.current();

        assertNotNull(current);
        assertNotNull(current.filesInCatalogGauge());
        assertEquals(1.0d, current.filesInCatalogGauge().value());
    }
}
