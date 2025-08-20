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

@RestController
@RequestMapping("file-catalog")
@Scope("prototype")
public class FileCatalogController {

    private FileCatalogService fileCatalogService;

    @Autowired
    public FileCatalogController(FileCatalogService fileCatalogService){
        this.fileCatalogService = fileCatalogService;
    }

    @GetMapping
    public List<FileCatalogItem> getByFileName(@RequestParam("fileName") String fileName){
        return fileCatalogService.findByFileNameContains(fileName);
    }

    @GetMapping("/similar")
    public List<FileCatalogItem> getSimilarByFileName(@RequestParam("fileName") String fileName){
        return fileCatalogService.findByFileNameSimilar(fileName);
    }

    @GetMapping("/archived-range")
    public List<FileCatalogItem> getArchivedItemsByDateRange(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "path", required = false) Optional<String> path){
        return fileCatalogService.findByArchiveDateBetweenAndAbsolutePathStartsWith(startDate, endDate, path);
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> performReconcile(){
        throw new UnsupportedOperationException("Will be available in future versions");
    }
}
