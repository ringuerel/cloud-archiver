package com.homelab.ringue.cloud.archiver.cloudprovider;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class CloudProviderFactory {

    private Map<CloudProviders,CloudProvider> cloudProviders = new HashMap<>();

    @Autowired
    public CloudProviderFactory(@Qualifier("no_provider") CloudProvider noProvider,@Qualifier("gcp")CloudProvider gcpCloudProvider){
        this.cloudProviders.put(CloudProviders.NO_PROVIDER, noProvider);
        this.cloudProviders.put(CloudProviders.GCP, gcpCloudProvider);
    }

    public CloudProvider getCloudProvider(CloudProviders cloudProviderName){
        return cloudProviders.get(cloudProviderName);
    }

}
