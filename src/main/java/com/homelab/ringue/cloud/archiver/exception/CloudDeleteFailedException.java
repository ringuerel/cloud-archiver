package com.homelab.ringue.cloud.archiver.exception;

public class CloudDeleteFailedException extends RuntimeException {
    public CloudDeleteFailedException(String objectName){
        super("Unable to get deletion from cloud on "+objectName);
    }
    
}
