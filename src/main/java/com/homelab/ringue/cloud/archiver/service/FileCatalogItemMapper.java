package com.homelab.ringue.cloud.archiver.service;

import java.nio.file.Path;
import java.time.Instant;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

public interface FileCatalogItemMapper {

    FileCatalogItem mapFromPath(Path path);

    FileCatalogItem mapFromFileCatalogItemAddArchiveDate(FileCatalogItem fileCatalogItem);

    FileCatalogItem mapFromFileCatalogItemUpdateCheckSum(FileCatalogItem fileCatalogItem, String checkSum);

    FileCatalogItem mapFromFileCatalogItemUpdateLastModified(FileCatalogItem fileCatalogItem, Instant lastModified);

}
