package com.homelab.ringue.cloud.archiver.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
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

    @Operation(summary = "Perform reconciliation (sync)",
               description = "Initiates a reconciliation process for file catalog items. (Currently unsupported)",
               responses = {
                   @ApiResponse(responseCode = "501", description = "Not Implemented")
               })
    @PostMapping("/sync")
    public ResponseEntity<Void> performReconcile(){
        throw new UnsupportedOperationException("Will be available in future versions");
    }
}
