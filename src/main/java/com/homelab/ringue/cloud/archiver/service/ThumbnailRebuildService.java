package com.homelab.ringue.cloud.archiver.service;

import java.util.Optional;

import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildMode;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildSummary;

public interface ThumbnailRebuildService {

    /**
     * Rebuilds thumbnails for catalog entries matching the given filters.
     *
     * @param mode           controls which entries are eligible (MISSING_ONLY, FAILED_ONLY, FORCE)
     * @param path           optional catalog path prefix filter
     * @param fileNameContains optional case-insensitive filename substring filter
     * @param limit          maximum number of items to process (defaults to configured value)
     * @param concurrency    maximum parallel thumbnail workers for this request (clamped 1–16)
     * @return summary of processed / created / skipped / failed counts
     */
    ThumbnailRebuildSummary rebuildThumbnails(
            ThumbnailRebuildMode mode,
            Optional<String> path,
            Optional<String> fileNameContains,
            Optional<Integer> limit,
            Optional<Integer> concurrency);
}
