package com.homelab.ringue.cloud.archiver.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
