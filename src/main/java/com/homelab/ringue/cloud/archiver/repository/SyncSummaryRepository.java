package com.homelab.ringue.cloud.archiver.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;

@Repository
public interface SyncSummaryRepository extends MongoRepository<SyncSummaryItem,String>{
}
