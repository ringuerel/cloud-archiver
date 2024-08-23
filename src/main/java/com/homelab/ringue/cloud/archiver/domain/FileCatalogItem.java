package com.homelab.ringue.cloud.archiver.domain;

import java.time.LocalDateTime;
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
    LocalDateTime lastModified
    ) {}
