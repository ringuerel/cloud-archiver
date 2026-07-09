package com.homelab.ringue.cloud.archiver.controller;

import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildMode;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildSummary;
import com.homelab.ringue.cloud.archiver.service.ThumbnailRebuildService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("thumbnails")
@Tag(name = "Thumbnails", description = "API for managing local thumbnail cache")
public class ThumbnailController {

    private final ThumbnailRebuildService thumbnailRebuildService;

    public ThumbnailController(ThumbnailRebuildService thumbnailRebuildService) {
        this.thumbnailRebuildService = thumbnailRebuildService;
    }

    @Operation(
            summary = "Rebuild thumbnails",
            description = "Creates or refreshes thumbnail metadata for catalog entries without re-uploading original files.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Thumbnail rebuild completed"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @PostMapping("/rebuild")
    public ThumbnailRebuildSummary rebuildThumbnails(
            @Parameter(description = "Rebuild mode: MISSING_ONLY, FAILED_ONLY, or FORCE")
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") ThumbnailRebuildMode mode,
            @Parameter(description = "Optional catalog path prefix")
            @RequestParam(value = "path", required = false) Optional<String> path,
            @Parameter(description = "Optional case-insensitive filename substring")
            @RequestParam(value = "fileNameContains", required = false) Optional<String> fileNameContains,
            @Parameter(description = "Maximum number of items to process")
            @RequestParam(value = "limit", required = false) Optional<Integer> limit,
            @Parameter(description = "Maximum number of thumbnail rebuild workers for this request")
            @RequestParam(value = "concurrency", required = false) Optional<Integer> concurrency) {
        return thumbnailRebuildService.rebuildThumbnails(mode, path, fileNameContains, limit, concurrency);
    }
}
