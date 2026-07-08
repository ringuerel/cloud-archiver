package com.homelab.ringue.cloud.archiver.domain.gallery;

import java.time.Instant;
import java.util.Date;

public record GalleryItem(
    // FileCatalogItem scalar fields
    String absolutePath,
    String fileName,
    String fileExtension,
    String parentFolder,
    boolean isDirectory,
    Long fileSize,
    Date archiveDate,
    String crc32c,
    Instant lastModified,
    String thumbnailPath,
    String thumbnailProvider,
    String thumbnailContentType,
    Instant thumbnailCreatedAt,
    String thumbnailStatus,
    String thumbnailError,
    // Computed URL fields
    String thumbnailUrl,
    String originalUrl,
    String statusUrl,
    String restoreUrl,
    // Computed availability flags
    boolean thumbnailAvailable,
    boolean originalAvailable,
    boolean restoreAvailable,
    boolean restoreInProgress
) {}
