package com.homelab.ringue.cloud.archiver.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.RebuildConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildMode;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildSummary;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.service.ThumbnailRebuildService;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ThumbnailRebuildServiceImpl implements ThumbnailRebuildService {

    private final FileCatalogItemRepository fileCatalogItemRepository;
    private final ThumbnailService thumbnailService;
    private final ApplicationProperties applicationProperties;

    public ThumbnailRebuildServiceImpl(
            FileCatalogItemRepository fileCatalogItemRepository,
            ThumbnailService thumbnailService,
            ApplicationProperties applicationProperties) {
        this.fileCatalogItemRepository = fileCatalogItemRepository;
        this.thumbnailService = thumbnailService;
        this.applicationProperties = applicationProperties;
    }

    @Override
    public ThumbnailRebuildSummary rebuildThumbnails(
            ThumbnailRebuildMode mode,
            Optional<String> path,
            Optional<String> fileNameContains,
            Optional<Integer> limit,
            Optional<Integer> concurrency) {

        RebuildConfig rebuildConfig = applicationProperties.getThumbnailsConfig().getRebuild();
        int pageSize = rebuildConfig.getPageSize();
        int maxItems = limit.orElse(rebuildConfig.getDefaultLimit());
        int maxConcurrency = concurrency
                .map(RebuildConfig::clampMaxConcurrency)
                .orElseGet(rebuildConfig::getMaxConcurrency);
        String fixedPath = path.map(this::fixLocationPath).orElse(null);

        int processed = 0;
        int created = 0;
        int skipped = 0;
        int failed = 0;
        Pageable pageRequest = PageRequest.ofSize(pageSize);
        Page<FileCatalogItem> page;
        ExecutorService thumbnailRebuildExecutor = Executors.newFixedThreadPool(maxConcurrency);

        try {
            do {
                page = queryForThumbnailRebuild(fileNameContains, fixedPath, pageRequest);
                if (processed >= maxItems) {
                    return new ThumbnailRebuildSummary(mode, processed, created, skipped, failed);
                }

                int remainingItems = maxItems - processed;
                List<FileCatalogItem> itemsToProcess = page.getContent().stream()
                        .filter(item -> shouldRebuildThumbnail(item, mode))
                        .limit(remainingItems)
                        .toList();

                List<ThumbnailRebuildResult> results = rebuildThumbnailBatch(
                        itemsToProcess,
                        mode == ThumbnailRebuildMode.FORCE,
                        thumbnailRebuildExecutor);

                processed += results.size();
                for (ThumbnailRebuildResult result : results) {
                    if (ThumbnailStatus.CREATED.name().equals(result.thumbnailStatus())) {
                        created++;
                    } else if (ThumbnailStatus.FAILED.name().equals(result.thumbnailStatus())) {
                        failed++;
                    } else {
                        skipped++;
                    }
                }
                pageRequest = page.nextPageable();
            } while (page.hasNext());
        } finally {
            thumbnailRebuildExecutor.shutdown();
        }

        return new ThumbnailRebuildSummary(mode, processed, created, skipped, failed);
    }

    private Page<FileCatalogItem> queryForThumbnailRebuild(Optional<String> fileNameContains, String fixedPath,
            Pageable pageRequest) {
        if (fileNameContains.isPresent() && fixedPath != null) {
            return fileCatalogItemRepository.findByFileNameContainsIgnoreCaseAndParentFolderStartsWith(
                    fileNameContains.get(), fixedPath, pageRequest);
        }
        if (fileNameContains.isPresent()) {
            return fileCatalogItemRepository.findByFileNameContainsIgnoreCase(fileNameContains.get(), pageRequest);
        }
        if (fixedPath != null) {
            return fileCatalogItemRepository.findByParentFolderStartsWith(fixedPath, pageRequest);
        }
        return fileCatalogItemRepository.findAll(pageRequest);
    }

    private boolean shouldRebuildThumbnail(FileCatalogItem item, ThumbnailRebuildMode mode) {
        return switch (mode) {
            case FORCE -> true;
            case FAILED_ONLY -> ThumbnailStatus.FAILED.name().equals(item.thumbnailStatus());
            case MISSING_ONLY -> (item.thumbnailPath() == null || item.thumbnailPath().isBlank())
                    && !ThumbnailStatus.SKIPPED.name().equals(item.thumbnailStatus());
        };
    }

    private List<ThumbnailRebuildResult> rebuildThumbnailBatch(List<FileCatalogItem> itemsToProcess, boolean force,
            ExecutorService thumbnailRebuildExecutor) {
        List<Callable<ThumbnailRebuildResult>> rebuildTasks = itemsToProcess.stream()
                .<Callable<ThumbnailRebuildResult>>map(item -> () -> {
                    FileCatalogItem updatedItem = thumbnailService.createOrUpdateThumbnail(item, force);
                    fileCatalogItemRepository.save(updatedItem);
                    return new ThumbnailRebuildResult(updatedItem.thumbnailStatus());
                })
                .toList();
        try {
            return thumbnailRebuildExecutor.invokeAll(rebuildTasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while rebuilding thumbnails", e);
                        } catch (ExecutionException e) {
                            throw new IllegalStateException("Failed rebuilding thumbnails", e.getCause());
                        }
                    })
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while rebuilding thumbnails", e);
        }
    }

    private String fixLocationPath(String locationPath) {
        CharSequence charSequence = new SimpleMessage("\\\\");
        return locationPath.replace(charSequence, "\\");
    }

    private record ThumbnailRebuildResult(String thumbnailStatus) {}
}
