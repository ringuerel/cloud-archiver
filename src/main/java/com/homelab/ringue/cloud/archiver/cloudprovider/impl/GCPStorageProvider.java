package com.homelab.ringue.cloud.archiver.cloudprovider.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BlobInfo.Builder;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProvider;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.exception.CloudDeleteFailedException;

import lombok.extern.slf4j.Slf4j;

@Component
@Qualifier("gcp")
@Slf4j
public class GCPStorageProvider implements CloudProvider{

    private ApplicationProperties applicationProperties;

    @Autowired
    public GCPStorageProvider(ApplicationProperties applicationProperties){
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void upload(FileCatalogItem fileCatalogItem) throws IOException {
        String gcpObjectName = getGcpObjectName(fileCatalogItem);
        String gcpBucketName = applicationProperties.getCloudProviderConfig().getBucketName();
        String gcpProjectId = applicationProperties.getCloudProviderConfig().getProjectId();
        Storage storage = getConfiguredStorage(gcpProjectId);
        BlobId blobId = getBlobId(gcpObjectName, gcpBucketName);
        Builder blobInfoBuilder = BlobInfo.newBuilder(blobId);
        //If no configured class it'll not be added tothe builder and uses bucket's default
        Optional.ofNullable(applicationProperties.getCloudProviderConfig().getStorageClass()).ifPresent(configuredStorageClass-> blobInfoBuilder.setStorageClass(StorageClass.valueOfStrict(configuredStorageClass)));

        BlobInfo blobInfo = blobInfoBuilder.build();
        // Optional: set a generation-match precondition to avoid potential race
        // conditions and data corruptions. The request returns a 412 error if the
        // preconditions are not met.
        Storage.BlobWriteOption precondition;
        if (storage.get(gcpBucketName, gcpObjectName) == null) {
        // For a target object that does not yet exist, set the DoesNotExist precondition.
        // This will cause the request to fail if the object is created before the request runs.
        precondition = Storage.BlobWriteOption.doesNotExist();
        } else {
        // If the destination already exists in your bucket, instead set a generation-match
        // precondition. This will cause the request to fail if the existing object's generation
        // changes before the request runs.
        precondition =
            Storage.BlobWriteOption.generationMatch(
                storage.get(gcpBucketName, gcpObjectName).getGeneration());
        }
        Blob uploadedFileMetadata = storage.createFrom(blobInfo, Paths.get(fileCatalogItem.absolutePath()), precondition);
        log.debug("UPLOADED==> {} to {} with {} provider MD5 {}",gcpObjectName,gcpBucketName,applicationProperties.getCloudProviderConfig().getType(),uploadedFileMetadata.getMd5());
    }

    private String getGcpObjectName(FileCatalogItem fileCatalogItem) {
        return fileCatalogItem.absolutePath().replaceAll("\\\\","/");
    }

    private BlobId getBlobId(String gcpObjectName, String gcpBucketName) {
        return BlobId.of(gcpBucketName, gcpObjectName);
    }

    private Storage getConfiguredStorage(String gcpProjectId) throws FileNotFoundException, IOException {
        Credentials providedCredentials = setupCredentials();
        Storage storage = StorageOptions.newBuilder()
        .setCredentials(providedCredentials)
        .setProjectId(gcpProjectId).build().getService();
        return storage;
    }

    @Override
    public void delete(FileCatalogItem fileCatalogItem) throws IOException {
        String gcpObjectName = getGcpObjectName(fileCatalogItem);
        String gcpBucketName = applicationProperties.getCloudProviderConfig().getBucketName();
        String gcpProjectId = applicationProperties.getCloudProviderConfig().getProjectId();
        Storage storage = getConfiguredStorage(gcpProjectId);
        BlobId blobId = getBlobId(gcpObjectName, gcpBucketName);
        boolean deleteResult = storage.delete(blobId);
        if(deleteResult){
            log.debug("DELETED==> {} from {} on {} provider",gcpObjectName,gcpBucketName,applicationProperties.getCloudProviderConfig().getType());
        }else{
            throw new CloudDeleteFailedException(gcpObjectName);
        }
    }

    public Credentials setupCredentials() throws FileNotFoundException, IOException{
        return GoogleCredentials.fromStream(new FileInputStream(applicationProperties.getCloudProviderConfig().getCredentialsFilePath()));
    }

}
