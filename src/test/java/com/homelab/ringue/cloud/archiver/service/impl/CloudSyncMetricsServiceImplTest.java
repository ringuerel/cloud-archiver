package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.CloudSyncMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CloudSyncMetricsServiceImplTest {

    @Test
    void currentReturnsRegisteredMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileCatalogItemRepository repository = Mockito.mock(FileCatalogItemRepository.class);
        Mockito.when(repository.count()).thenReturn(1L);

        CloudSyncMetricsServiceImpl service = new CloudSyncMetricsServiceImpl(meterRegistry, repository);

        CloudSyncMetrics current = service.current();

        assertNotNull(current);
        assertNotNull(current.filesInCatalogGauge());
        assertEquals(1.0d, current.filesInCatalogGauge().value());
    }

    @Test
    void currentReturnsSameInstanceOnEveryCall() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileCatalogItemRepository repository = Mockito.mock(FileCatalogItemRepository.class);

        CloudSyncMetricsServiceImpl service = new CloudSyncMetricsServiceImpl(meterRegistry, repository);

        assertNotNull(service.current());
        assertEquals(service.current(), service.current());
    }
}
