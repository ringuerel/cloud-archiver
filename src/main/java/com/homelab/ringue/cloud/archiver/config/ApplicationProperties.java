package com.homelab.ringue.cloud.archiver.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviders;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;

@Component
@ConfigurationProperties(prefix = "application")
@Data
@EnableScheduling
public class ApplicationProperties {
    private List<ScanLocationConfig> scanFolders;
    private CloudProviderConfig cloudProviderConfig;

    @Data
    public static class CloudProviderConfig{
        private CloudProviders type;
        private String bucketName;
        private String projectId;
        private String credentialsFilePath;
        private String storageClass;
    }

    @Data
    @NoArgsConstructor
    public static class ScanLocationConfig{
        private String scanFolder;
        private boolean ignoreHiddenFiles;
        private boolean cleanRemovedFromCloud;
        private Integer standardDeleteDaysLimit;
        private Integer archiveDeleteDaysHold;
        private List<String> ignorePatterns;
        private int collectionFetchSize;
        private List<Pattern> compiledIgnorePatterns;

        @PostConstruct
        public void initScanConfigLocation(){
            compiledIgnorePatterns = Optional.ofNullable(ignorePatterns).orElse(Collections.emptyList()).stream().map(Pattern::compile).collect(Collectors.toList());
        }

        public List<String> getIgnorePatterns(){
            return Optional.ofNullable(ignorePatterns).orElse(Collections.emptyList());
        }

        public int getCollectionFetchSize(){
            if(collectionFetchSize <= 0){
                return 500;
            }
            return collectionFetchSize;
        }

        public ScanLocationConfig(ScanLocationConfig locationConfig) {
            this.scanFolder = locationConfig.getScanFolder();
            this.ignoreHiddenFiles = locationConfig.isIgnoreHiddenFiles();
            this.cleanRemovedFromCloud = locationConfig.isCleanRemovedFromCloud();
            this.compiledIgnorePatterns = locationConfig.getCompiledIgnorePatterns();
            this.collectionFetchSize = locationConfig.getCollectionFetchSize();
            this.standardDeleteDaysLimit = locationConfig.getStandardDeleteDaysLimit();
            this.archiveDeleteDaysHold = locationConfig.getArchiveDeleteDaysHold();
        }
    }
}
