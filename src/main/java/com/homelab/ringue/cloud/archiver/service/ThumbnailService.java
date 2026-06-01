package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

public interface ThumbnailService {

    FileCatalogItem createOrUpdateThumbnail(FileCatalogItem fileCatalogItem, boolean force);

    void deleteThumbnail(FileCatalogItem fileCatalogItem);
}
