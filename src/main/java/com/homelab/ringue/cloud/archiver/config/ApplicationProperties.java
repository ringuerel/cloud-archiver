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
    private ThumbnailsConfig thumbnailsConfig;
    private Integer crc32cBufferSize;
    private String downloadRoot;
    private Long syncLockTimeoutSeconds;

    public int getCrc32cBufferSize(){
        return Optional.ofNullable(crc32cBufferSize).orElse(1024);
    }

    public String getDownloadRoot() {
        return downloadRoot;
    }

    public Long getSyncLockTimeoutSeconds() {
        return Optional.ofNullable(syncLockTimeoutSeconds).orElse(3600L); // Default to 1 hour
    }

    public ThumbnailsConfig getThumbnailsConfig() {
        return Optional.ofNullable(thumbnailsConfig).orElseGet(ThumbnailsConfig::new);
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
        private boolean embedEnabled;
        public String getSummaryTemplateText(){
            return Optional.ofNullable(summaryTemplateText).orElse(DEFAULT_SUMMARY_TEMPLATE);
        }
    }

    @Data
    public static class ThumbnailsConfig {
        private boolean enabled;
        private String mode;
        private String localRoot;
        private Integer maxWidth;
        private Integer maxHeight;
        private String outputFormat;
        private String ffmpegPath;
        private String ffprobePath;
        private String heifConvertPath;
        private Integer commandTimeoutSeconds;
        private RebuildConfig rebuild;

        public String getMode() {
            return Optional.ofNullable(mode).orElse("GENERATE");
        }

        public String getLocalRoot() {
            return Optional.ofNullable(localRoot).orElse("/thumbnails");
        }

        public int getMaxWidth() {
            return Optional.ofNullable(maxWidth).orElse(512);
        }

        public int getMaxHeight() {
            return Optional.ofNullable(maxHeight).orElse(512);
        }

        public String getOutputFormat() {
            return Optional.ofNullable(outputFormat).orElse("jpg");
        }

        public String getFfmpegPath() {
            return Optional.ofNullable(ffmpegPath).orElse("ffmpeg");
        }

        public String getFfprobePath() {
            return Optional.ofNullable(ffprobePath).orElse("ffprobe");
        }

        public String getHeifConvertPath() {
            return Optional.ofNullable(heifConvertPath).orElse("heif-convert");
        }

        public int getCommandTimeoutSeconds() {
            return Optional.ofNullable(commandTimeoutSeconds).orElse(30);
        }

        public RebuildConfig getRebuild() {
            return Optional.ofNullable(rebuild).orElseGet(RebuildConfig::new);
        }
    }

    @Data
    public static class RebuildConfig {
        private static final int DEFAULT_MAX_CONCURRENCY = 2;
        private static final int MIN_MAX_CONCURRENCY = 1;
        private static final int MAX_MAX_CONCURRENCY = 16;

        private Integer pageSize;
        private Integer defaultLimit;
        private Integer maxConcurrency;

        public int getPageSize() {
            return Optional.ofNullable(pageSize).orElse(500);
        }

        public int getDefaultLimit() {
            return Optional.ofNullable(defaultLimit).orElse(500);
        }

        public int getMaxConcurrency() {
            return clampMaxConcurrency(Optional.ofNullable(maxConcurrency).orElse(DEFAULT_MAX_CONCURRENCY));
        }

        public static int clampMaxConcurrency(int maxConcurrency) {
            return Math.max(MIN_MAX_CONCURRENCY, Math.min(MAX_MAX_CONCURRENCY, maxConcurrency));
        }
    }

    @Data
    @NoArgsConstructor
    public static class ScanLocationConfig{
        private String scanFolder;
        private boolean ignoreHiddenFiles;
        private boolean cleanRemovedFromCloud;
        private boolean deleteIfEmptyEnabled;
        private Integer standardDeleteDaysLimit;
        private Integer archiveDeleteDaysHold;
        private String thumbnailRoot;
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
            this.deleteIfEmptyEnabled = locationConfig.isDeleteIfEmptyEnabled();
            this.compiledIgnorePatterns = locationConfig.getCompiledIgnorePatterns();
            this.collectionFetchSize = locationConfig.getCollectionFetchSize();
            this.standardDeleteDaysLimit = locationConfig.getStandardDeleteDaysLimit();
            this.archiveDeleteDaysHold = locationConfig.getArchiveDeleteDaysHold();
            this.thumbnailRoot = locationConfig.getThumbnailRoot();
        }
    }
}
