package com.homelab.ringue.cloud.archiver.service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

public record BackupPipelineContext(
        Map<String, FileCatalogItem> catalogCache,
        AtomicInteger uploadedCount,
        AtomicLong uploadedSize) {
}
