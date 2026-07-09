package com.homelab.ringue.cloud.archiver.service.impl;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProvider;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviders;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.domain.gallery.MediaStatusResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJob;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJobStatus;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreResponse;
import com.homelab.ringue.cloud.archiver.exception.GalleryItemNotFoundException;
import com.homelab.ringue.cloud.archiver.exception.MissingPathException;
import com.homelab.ringue.cloud.archiver.exception.RestoreNotAvailableException;
import com.homelab.ringue.cloud.archiver.exception.ThumbnailFailedException;
import com.homelab.ringue.cloud.archiver.exception.ThumbnailSkippedException;
import com.homelab.ringue.cloud.archiver.gallery.ContentSecurityGuard;
import com.homelab.ringue.cloud.archiver.gallery.RestoreJobRegistry;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.MediaService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private static final String THUMBNAIL_CACHE_CONTROL = "public, max-age=86400";
    private static final String ORIGINAL_CACHE_CONTROL = "private, max-age=0";

    private final FileCatalogItemRepository fileCatalogItemRepository;
    private final ContentSecurityGuard contentSecurityGuard;
    private final RestoreJobRegistry restoreJobRegistry;
    private final ApplicationProperties applicationProperties;
    private final CloudProviderFactory cloudProviderFactory;

    public MediaServiceImpl(
            FileCatalogItemRepository fileCatalogItemRepository,
            ContentSecurityGuard contentSecurityGuard,
            RestoreJobRegistry restoreJobRegistry,
            ApplicationProperties applicationProperties,
            CloudProviderFactory cloudProviderFactory) {
        this.fileCatalogItemRepository = fileCatalogItemRepository;
        this.contentSecurityGuard = contentSecurityGuard;
        this.restoreJobRegistry = restoreJobRegistry;
        this.applicationProperties = applicationProperties;
        this.cloudProviderFactory = cloudProviderFactory;
    }

    @Override
    public ResponseEntity<?> streamThumbnail(String absolutePath, String ifNoneMatch) {
        FileCatalogItem item = findItem(absolutePath, "THUMBNAIL_NOT_FOUND");
        if (ThumbnailStatus.FAILED.name().equals(item.thumbnailStatus())) {
            throw new ThumbnailFailedException(item.thumbnailError());
        }
        if (ThumbnailStatus.SKIPPED.name().equals(item.thumbnailStatus())) {
            throw new ThumbnailSkippedException(absolutePath);
        }
        if (!ThumbnailStatus.CREATED.name().equals(item.thumbnailStatus()) || !StringUtils.hasText(item.thumbnailPath())
                || !Files.exists(Path.of(item.thumbnailPath()))) {
            throw new GalleryItemNotFoundException("THUMBNAIL_NOT_FOUND", absolutePath);
        }

        Path resolvedPath = contentSecurityGuard.validateAndResolve(item.thumbnailPath());
        return streamFile(
                resolvedPath,
                item.thumbnailContentType(),
                THUMBNAIL_CACHE_CONTROL,
                null,
                ifNoneMatch);
    }

    @Override
    public ResponseEntity<?> streamOriginal(String absolutePath, String ifNoneMatch) {
        FileCatalogItem item = findItem(absolutePath, "ORIGINAL_NOT_FOUND");
        Optional<RestoreJob> activeJob = activeRestoreJob(absolutePath);
        if (activeJob.isPresent()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("status", "RESTORE_IN_PROGRESS", "restoreJobId", activeJob.get().jobId()));
        }

        if (!Files.exists(Path.of(item.absolutePath())) && item.archiveDate() != null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "ORIGINAL_NOT_LOCAL",
                            "restoreAvailable", true,
                            "restoreInProgress", false));
        }

        if (!Files.exists(Path.of(item.absolutePath()))) {
            throw new GalleryItemNotFoundException("ORIGINAL_NOT_FOUND", absolutePath);
        }

        Path resolvedPath = contentSecurityGuard.validateAndResolve(item.absolutePath());
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(item.fileName(), StandardCharsets.UTF_8)
                .build();
        return streamFile(
                resolvedPath,
                probeContentType(resolvedPath),
                ORIGINAL_CACHE_CONTROL,
                contentDisposition,
                ifNoneMatch);
    }

    @Override
    public MediaStatusResponse getStatus(String absolutePath) {
        FileCatalogItem item = findItem(absolutePath, "ITEM_NOT_FOUND");
        boolean thumbnailAvailable = ThumbnailStatus.CREATED.name().equals(item.thumbnailStatus())
                && StringUtils.hasText(item.thumbnailPath())
                && Files.exists(Path.of(item.thumbnailPath()));
        boolean originalAvailable = !item.isDirectory() && Files.exists(Path.of(item.absolutePath()));
        Optional<RestoreJob> activeJob = activeRestoreJob(absolutePath);
        boolean restoreAvailable = !originalAvailable && item.archiveDate() != null;

        OriginalMetadata originalMetadata = originalAvailable
                ? originalMetadata(Path.of(item.absolutePath()))
                : new OriginalMetadata(null, null, null);

        return new MediaStatusResponse(
                new MediaStatusResponse.ThumbnailStatus(
                        thumbnailAvailable,
                        thumbnailAvailable ? thumbnailUrl(absolutePath) : null,
                        item.thumbnailContentType(),
                        item.thumbnailStatus(),
                        item.thumbnailError(),
                        item.thumbnailCreatedAt()),
                new MediaStatusResponse.OriginalStatus(
                        originalAvailable,
                        originalAvailable ? originalUrl(absolutePath) : null,
                        originalMetadata.contentType(),
                        originalMetadata.fileSize(),
                        originalMetadata.lastModified()),
                new MediaStatusResponse.RestoreStatus(
                        restoreAvailable,
                        activeJob.isPresent(),
                        activeJob.map(RestoreJob::jobId).orElse(null)));
    }

    @Override
    public RestoreResponse restore(String absolutePath) {
        if (!StringUtils.hasText(absolutePath)) {
            throw new MissingPathException();
        }

        FileCatalogItem item = findItem(absolutePath, "ITEM_NOT_FOUND");
        if (Files.exists(Path.of(item.absolutePath()))) {
            return new RestoreResponse("ALREADY_AVAILABLE", null, absolutePath, originalUrl(absolutePath), null);
        }

        Optional<RestoreJob> activeJob = activeRestoreJob(absolutePath);
        if (activeJob.isPresent()) {
            return new RestoreResponse("ALREADY_IN_PROGRESS", activeJob.get().jobId(), absolutePath, null,
                    applicationProperties.getDownloadRoot());
        }

        CloudProviders providerType = cloudProviderType();
        String downloadRoot = applicationProperties.getDownloadRoot();
        if (item.archiveDate() == null || providerType == null || providerType == CloudProviders.NO_PROVIDER
                || !StringUtils.hasText(downloadRoot)) {
            throw new RestoreNotAvailableException(absolutePath);
        }

        RestoreJob job = restoreJobRegistry.registerJob(absolutePath);
        CloudProvider cloudProvider = cloudProviderFactory.getCloudProvider(providerType);
        CompletableFuture.runAsync(() -> {
            try {
                cloudProvider.download(absolutePath, downloadRoot);
                restoreJobRegistry.completeJob(absolutePath);
            } catch (IOException | RuntimeException e) {
                restoreJobRegistry.failJob(absolutePath);
                log.error("Restore job failed for {}", absolutePath, e);
            }
        });

        return new RestoreResponse("QUEUED", job.jobId(), absolutePath, null, downloadRoot);
    }

    private ResponseEntity<?> streamFile(
            Path resolvedPath,
            String contentType,
            String cacheControl,
            ContentDisposition contentDisposition,
            String ifNoneMatch) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(resolvedPath, BasicFileAttributes.class);
            String etag = MediaService.computeETag(attributes.size(), attributes.lastModifiedTime().toMillis());
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ETAG, etag);
            headers.set(HttpHeaders.CACHE_CONTROL, cacheControl);
            headers.setContentLength(attributes.size());
            if (contentDisposition != null) {
                headers.setContentDisposition(contentDisposition);
            }

            if (etag.equals(ifNoneMatch)) {
                return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
            }

            headers.setContentType(MediaType.parseMediaType(
                    StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE));
            return new ResponseEntity<>(new FileSystemResource(resolvedPath), headers, HttpStatus.OK);
        } catch (IOException e) {
            throw new GalleryItemNotFoundException("MEDIA_NOT_FOUND", resolvedPath.toString());
        }
    }

    private FileCatalogItem findItem(String absolutePath, String errorCode) {
        return fileCatalogItemRepository.findById(absolutePath)
                .orElseThrow(() -> new GalleryItemNotFoundException(errorCode, absolutePath));
    }

    private Optional<RestoreJob> activeRestoreJob(String absolutePath) {
        return restoreJobRegistry.findJob(absolutePath)
                .filter(job -> job.status() == RestoreJobStatus.RUNNING);
    }

    private OriginalMetadata originalMetadata(Path originalPath) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(originalPath, BasicFileAttributes.class);
            return new OriginalMetadata(
                    probeContentType(originalPath),
                    attributes.size(),
                    attributes.lastModifiedTime().toInstant());
        } catch (IOException e) {
            return new OriginalMetadata(null, null, null);
        }
    }

    private String probeContentType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private CloudProviders cloudProviderType() {
        return applicationProperties.getCloudProviderConfig() == null
                ? null
                : applicationProperties.getCloudProviderConfig().getType();
    }

    private String thumbnailUrl(String absolutePath) {
        return "/file-catalog/media/thumbnail?path=" + encode(absolutePath);
    }

    private String originalUrl(String absolutePath) {
        return "/file-catalog/media/original?path=" + encode(absolutePath);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OriginalMetadata(String contentType, Long fileSize, Instant lastModified) {}
}
