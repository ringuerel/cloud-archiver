package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;

class FileCatalogItemMapperImplTest {

    private final FileCatalogItemMapperImpl mapper = new FileCatalogItemMapperImpl();

    @Test
    void mapFromFileCatalogItemUpdateThumbnail_updatesOnlyThumbnailFields() {
        Date archiveDate = new Date();
        Instant lastModified = Instant.now();
        Instant thumbnailCreatedAt = Instant.now();
        FileCatalogItem item = new FileCatalogItem(
                "/scan/photo.jpg",
                "photo.jpg",
                "jpg",
                "/scan",
                false,
                100L,
                archiveDate,
                "crc",
                lastModified);

        FileCatalogItem result = mapper.mapFromFileCatalogItemUpdateThumbnail(
                item,
                "/thumbs/photo.jpg",
                "GENERATED",
                "image/jpeg",
                thumbnailCreatedAt,
                ThumbnailStatus.CREATED.name(),
                null);

        assertEquals(item.absolutePath(), result.absolutePath());
        assertEquals(item.fileName(), result.fileName());
        assertEquals(item.fileExtension(), result.fileExtension());
        assertEquals(item.parentFolder(), result.parentFolder());
        assertEquals(item.fileSize(), result.fileSize());
        assertEquals(item.archiveDate(), result.archiveDate());
        assertEquals(item.crc32c(), result.crc32c());
        assertEquals(item.lastModified(), result.lastModified());
        assertEquals("/thumbs/photo.jpg", result.thumbnailPath());
        assertEquals("GENERATED", result.thumbnailProvider());
        assertEquals("image/jpeg", result.thumbnailContentType());
        assertEquals(thumbnailCreatedAt, result.thumbnailCreatedAt());
        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertNull(result.thumbnailError());
    }

    @Test
    void mapFromFileCatalogItemUpdateCheckSum_preservesThumbnailFields() {
        Instant thumbnailCreatedAt = Instant.now();
        FileCatalogItem item = new FileCatalogItem(
                "/scan/photo.jpg",
                "photo.jpg",
                "jpg",
                "/scan",
                false,
                100L,
                new Date(),
                "old-crc",
                Instant.now(),
                "/thumbs/photo.jpg",
                "GENERATED",
                "image/jpeg",
                thumbnailCreatedAt,
                ThumbnailStatus.CREATED.name(),
                null);

        FileCatalogItem result = mapper.mapFromFileCatalogItemUpdateCheckSum(item, "new-crc");

        assertEquals("new-crc", result.crc32c());
        assertEquals(item.thumbnailPath(), result.thumbnailPath());
        assertEquals(item.thumbnailProvider(), result.thumbnailProvider());
        assertEquals(item.thumbnailContentType(), result.thumbnailContentType());
        assertEquals(item.thumbnailCreatedAt(), result.thumbnailCreatedAt());
        assertEquals(item.thumbnailStatus(), result.thumbnailStatus());
        assertEquals(item.thumbnailError(), result.thumbnailError());
    }
}
