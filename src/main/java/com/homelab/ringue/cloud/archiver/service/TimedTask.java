package com.homelab.ringue.cloud.archiver.service;

import java.util.Collections;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TimedTask {

    private FileCatalogService fileCatalogService;
    private ApplicationProperties applicationProperties;

    @PostConstruct
    public void initTask(){
        log.info("Configured Provider: {}, Bucket: {}, StorageClass: {}",applicationProperties.getCloudProviderConfig().getType(),applicationProperties.getCloudProviderConfig().getBucketName(),applicationProperties.getCloudProviderConfig().getStorageClass());
        applicationProperties.getScanFolders().stream()
        .forEach(this::logFolderConfig);
    }

    @Autowired
    public TimedTask(FileCatalogService fileCatalogService, ApplicationProperties applicationProperties){
        this.fileCatalogService = fileCatalogService;
        this.applicationProperties = applicationProperties;
    }

    @Scheduled(cron = "${backup.schedule.cron}")
    public void performScheduledBackup() throws CloudBackupException{
        if(!Optional.ofNullable(applicationProperties.getScanFolders()).isPresent()){
            log.error("FATAL: No configured scan locations, application shutdown");
            System.exit(0);
        }
        applicationProperties.getScanFolders().forEach(this::processScanFolder);
    }

    private void logFolderConfig(ScanLocationConfig scanlocationconfig) {
        log.info("-Scan Location Config-");
        log.info("\tFolder: {}",scanlocationconfig.getScanFolder());
        log.info("\tFetch size: {}",scanlocationconfig.getCollectionFetchSize());
        log.info("\tIgnore hidden files: {}",scanlocationconfig.isIgnoreHiddenFiles());
        log.info("\tClean removed from cloud: {}",scanlocationconfig.isCleanRemovedFromCloud());
        log.info("\tIgnore patterns");
        Optional.ofNullable(scanlocationconfig.getIgnorePatterns()).orElse(Collections.emptyList())
        .forEach(ignorePattern -> log.info("\t\t{}", ignorePattern));
    }

    private void processScanFolder(ScanLocationConfig scanlocationconfig) {
        try {
            fileCatalogService.performLocationSync(scanlocationconfig);
        } catch (CloudBackupException e) {
            log.error("Failed during location backup  {}", e,scanlocationconfig.getScanFolder());
        }
    }

}
