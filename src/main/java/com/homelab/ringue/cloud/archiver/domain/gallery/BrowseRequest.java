package com.homelab.ringue.cloud.archiver.domain.gallery;

import org.springframework.web.bind.annotation.RequestParam;

public record BrowseRequest(
    @RequestParam(required = false) String path,
    @RequestParam(required = false) String cursor,
    @RequestParam(defaultValue = "60") int limit,
    @RequestParam(required = false) String fileNameContains,
    @RequestParam(required = false) String extensions,
    @RequestParam(required = false) String startDate,
    @RequestParam(required = false) String endDate,
    @RequestParam(required = false) String thumbnailStatus,
    @RequestParam(defaultValue = "false") boolean includeDirectories
) {}
