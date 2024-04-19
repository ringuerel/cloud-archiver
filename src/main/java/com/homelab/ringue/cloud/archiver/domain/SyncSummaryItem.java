package com.homelab.ringue.cloud.archiver.domain;

import java.time.Instant;

import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sync_summary")
public record SyncSummaryItem(
    @Id
    @BsonId()
    @BsonRepresentation(BsonType.OBJECT_ID)
    String syncDate,
    int uploadCount,
    long uploadSize,
    int deleteCount,
    long deleteSize,
    Instant lastUpdate) {}
