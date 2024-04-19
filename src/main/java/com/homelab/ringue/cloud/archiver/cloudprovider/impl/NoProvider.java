package com.homelab.ringue.cloud.archiver.cloudprovider.impl;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProvider;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Qualifier("no_provider")
@Primary
public class NoProvider implements CloudProvider{

    @Override
    public void upload(FileCatalogItem fileCatalogItem) {
        log.trace("{} uploaded",fileCatalogItem.absolutePath());
    }

    @Override
    public void delete(FileCatalogItem fileCatalogItem) throws IOException {
        log.trace("{} would have been deleted on cloud provider", fileCatalogItem.absolutePath());
    }

}
