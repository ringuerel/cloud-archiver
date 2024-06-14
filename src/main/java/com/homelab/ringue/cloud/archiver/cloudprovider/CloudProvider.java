package com.homelab.ringue.cloud.archiver.cloudprovider;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

public interface CloudProvider {

    void upload(FileCatalogItem fileCatalogItem) throws IOException;

    void delete(FileCatalogItem fileCatalogItem) throws IOException;

    String getCheckSum(FileCatalogItem fileCatalogItem) throws FileNotFoundException, IOException;

}
