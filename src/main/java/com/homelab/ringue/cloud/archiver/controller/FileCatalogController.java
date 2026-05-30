package com.homelab.ringue.cloud.archiver.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.PendingDeletionItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildMode;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildSummary;
import com.homelab.ringue.cloud.archiver.service.FileCatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("file-catalog")
@Scope("prototype")
@Tag(name = "File Catalog", description = "API for managing file catalog items")
public class FileCatalogController {

    private FileCatalogService fileCatalogService;

    @Autowired
    public FileCatalogController(FileCatalogService fileCatalogService){
        this.fileCatalogService = fileCatalogService;
    }

    @Operation(summary = "Get file catalog items by file name",
               description = "Returns a list of file catalog items that contain the specified file name.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Successfully retrieved list of items"),
                   @ApiResponse(responseCode = "500", description = "Internal server error")
               })
    @GetMapping
    public List<FileCatalogItem> getByFileName(
            @Parameter(description = "Part of the file name to search for") @RequestParam("fileName") String fileName){
        return fileCatalogService.findByFileNameContains(fileName);
    }

    @Operation(summary = "Get file catalog items with similar names",
               description = "Returns a list of file catalog items whose names are similar to the user's input.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Successfully retrieved list of similar items"),
                   @ApiResponse(responseCode = "500", description = "Internal server error")
               })
    @GetMapping("/similar")
    public List<FileCatalogItem> getSimilarByFileName(
            @Parameter(description = "File name to find similar items for") @RequestParam("fileName") String fileName){
        return fileCatalogService.findByFileNameSimilar(fileName);
    }

    @Operation(summary = "Get archived items by date range",
               description = "Returns a list of archived file catalog items within a specified date range and optional path.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Successfully retrieved list of archived items"),
                   @ApiResponse(responseCode = "500", description = "Internal server error")
               })
    @GetMapping("/archived-range")
    public List<FileCatalogItem> getArchivedItemsByDateRange(
            @Parameter(description = "Start date for the archive range (YYYY-MM-DD)") @RequestParam("startDate") String startDate,
            @Parameter(description = "End date for the archive range (YYYY-MM-DD)") @RequestParam("endDate") String endDate,
            @Parameter(description = "Optional path to filter archived items") @RequestParam(value = "path", required = false) Optional<String> path){
        return fileCatalogService.findByArchiveDateBetweenAndAbsolutePathStartsWith(startDate, endDate, path);
    }

    @Operation(summary = "Download file from cloud",
               description = "Initiates the download of a file from the cloud storage.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Download started successfully"),
                   @ApiResponse(responseCode = "500", description = "Download failed")
               })
    @PostMapping("/download")
    public ResponseEntity<String> downloadFromCloud(
            @Parameter(description = "Full path of the file in cloud storage") @RequestParam("path") String cloudPath) {
        boolean success = fileCatalogService.downloadFromCloud(cloudPath);
        if (success) {
            return ResponseEntity.ok("Download started for: " + cloudPath);
        } else {
            return ResponseEntity.status(500).body("Download failed for: " + cloudPath);
        }
    }

    @Operation(summary = "Get pending deletion items",
               description = "Returns catalog items that no longer exist on disk, enriched with the number of days until they are eligible for deletion based on the owning scan location's delete policy.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Successfully retrieved list of pending deletion items"),
                   @ApiResponse(responseCode = "500", description = "Internal server error")
               })
    @GetMapping("/pending-deletion")
    public List<PendingDeletionItem> getPendingDeletion(
            @Parameter(description = "Case-insensitive substring match against fileName") @RequestParam(value = "fileNameContains", required = false) Optional<String> fileNameContains,
            @Parameter(description = "Exact match against fileName") @RequestParam(value = "fileNameExact", required = false) Optional<String> fileNameExact,
            @Parameter(description = "Restrict to entries whose absolutePath starts with this prefix") @RequestParam(value = "path", required = false) Optional<String> path) {
        return fileCatalogService.findPendingDeletion(fileNameContains, fileNameExact, path);
    }

    @Operation(summary = "Rebuild thumbnails",
               description = "Creates or refreshes thumbnail metadata for catalog entries without re-uploading original files.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Thumbnail rebuild completed"),
                   @ApiResponse(responseCode = "500", description = "Internal server error")
               })
    @PostMapping("/thumbnails/rebuild")
    public ThumbnailRebuildSummary rebuildThumbnails(
            @Parameter(description = "Rebuild mode: MISSING_ONLY, FAILED_ONLY, or FORCE") @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") ThumbnailRebuildMode mode,
            @Parameter(description = "Optional catalog path prefix") @RequestParam(value = "path", required = false) Optional<String> path,
            @Parameter(description = "Optional case-insensitive filename substring") @RequestParam(value = "fileNameContains", required = false) Optional<String> fileNameContains,
            @Parameter(description = "Maximum number of items to process") @RequestParam(value = "limit", required = false) Optional<Integer> limit,
            @Parameter(description = "Maximum number of thumbnail rebuild workers for this request") @RequestParam(value = "concurrency", required = false) Optional<Integer> concurrency) {
        return fileCatalogService.rebuildThumbnails(mode, path, fileNameContains, limit, concurrency);
    }

    @Operation(summary = "Trigger a manual sync process",
               description = "Initiates a full synchronization process for all configured scan locations. This endpoint will prevent concurrent syncs by checking a lock. If a sync is already running, it will return a conflict status.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Sync process initiated successfully"),
                   @ApiResponse(responseCode = "409", description = "Sync process skipped: another sync is already running")
               })
    @PostMapping("/sync")
    public ResponseEntity<String> performReconcile(){
        boolean syncStarted = fileCatalogService.startAllLocationSyncs();
        if (syncStarted) {
            return ResponseEntity.ok("Sync process initiated successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Sync process skipped: another sync is already running.");
        }
    }
}
