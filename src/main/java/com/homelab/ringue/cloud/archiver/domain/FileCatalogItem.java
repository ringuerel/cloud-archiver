package com.homelab.ringue.cloud.archiver.domain;

import java.time.Instant;
import java.util.Date;

import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "file_catalog")
public record FileCatalogItem(
    @Id
    @BsonId()
    @BsonRepresentation(BsonType.OBJECT_ID)
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
    String thumbnailError
    ) {

    public FileCatalogItem(
        String absolutePath,
        String fileName,
        String fileExtension,
        String parentFolder,
        boolean isDirectory,
        Long fileSize,
        Date archiveDate,
        String crc32c,
        Instant lastModified
    ) {
        this(absolutePath, fileName, fileExtension, parentFolder, isDirectory, fileSize, archiveDate, crc32c,
                lastModified, null, null, null, null, null, null);
    }
}
