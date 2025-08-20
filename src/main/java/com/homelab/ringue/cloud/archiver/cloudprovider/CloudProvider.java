package com.homelab.ringue.cloud.archiver.cloudprovider;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

public interface CloudProvider {

    void upload(FileCatalogItem fileCatalogItem) throws IOException;

    void delete(FileCatalogItem fileCatalogItem) throws IOException;

    String getCheckSum(FileCatalogItem fileCatalogItem) throws FileNotFoundException, IOException;

    /**
     * Downloads a file or folder from the cloud provider to the specified local path.
     * @param cloudPath The path in the cloud provider
     * @param localTargetPath The local path to store the downloaded file/folder
     * @throws IOException if download fails
     */
    void download(String cloudPath, String localTargetPath) throws IOException;

}
