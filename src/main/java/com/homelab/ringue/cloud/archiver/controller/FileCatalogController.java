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

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.service.FileCatalogService;

@RestController
@RequestMapping("file-catalog")
@Scope("prototype")
public class FileCatalogController {

    private FileCatalogService fileCatalogService;
    private ApplicationProperties applicationProperties;

    @Autowired
    public FileCatalogController(FileCatalogService fileCatalogService, ApplicationProperties applicationProperties){
        this.fileCatalogService = fileCatalogService;
        this.applicationProperties = applicationProperties;
    }

    @GetMapping
    public List<FileCatalogItem> getByFileName(@RequestParam("fileName") String fileName){
        return fileCatalogService.findByFileNameContains(fileName);
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Void> performReconcile(){
        applicationProperties.getScanFolders().stream().forEach(fileCatalogService::performReconcile);
        return ResponseEntity.accepted().build();
    }
}
