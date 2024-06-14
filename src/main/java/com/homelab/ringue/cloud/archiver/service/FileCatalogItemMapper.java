package com.homelab.ringue.cloud.archiver.service;

import java.nio.file.Path;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

public interface FileCatalogItemMapper {

    public FileCatalogItem mapFromPath(Path path);

    public FileCatalogItem mapFromFileCatalogItemAddArchiveDateAndCheckSum(FileCatalogItem fileCatalogItem, String checkSum);

    public FileCatalogItem mapFromFileCatalogItemUpdateCheckSumOnly(FileCatalogItem fileCatalogItem, String checkSum);

}
