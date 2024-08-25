package com.homelab.ringue.cloud.archiver.config;

import java.net.URI;
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
    private NotificationsConfig notificationsConfig;
    private Integer crc32cBufferSize;

    public int getCrc32cBufferSize(){
        return Optional.ofNullable(crc32cBufferSize).orElse(1024);
    }

    @Data
    public static class CloudProviderConfig{
        private CloudProviders type;
        private String bucketName;
        private String projectId;
        private String credentialsFilePath;
        private String storageClass;
    }

    @Data
    public static class NotificationsConfig{
        public static final String IMPORTED_COUNT = "IMPORTED_COUNT";
        public static final String IMPORTED_SIZE = "IMPORTED_SIZE";
        public static final String DELETED_COUNT = "DELETED_COUNT";
        public static final String DELETED_SIZE = "DELETED_SIZE";
        public static final String SCAN_LOCATION = "SCAN_LOCATION";
        public static final String DEFAULT_INFO_PREFIX = "INFO: ";
        public static final String DEFAULT_ERROR_PREFIX = "ERROR: ";
        private static final String DEFAULT_SUMMARY_TEMPLATE = SCAN_LOCATION+" imported "+IMPORTED_COUNT+" size "+IMPORTED_SIZE+", deleted "+DELETED_COUNT+" size "+DELETED_SIZE;
        private URI uri;
        private String userName;
        private String summaryTemplateText;
        private String infoPrefix;
        private String errorPrefix;
        public String getSummaryTemplateText(){
            return Optional.ofNullable(summaryTemplateText).orElse(DEFAULT_SUMMARY_TEMPLATE);
        }
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
