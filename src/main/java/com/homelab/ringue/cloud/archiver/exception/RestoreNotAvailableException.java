package com.homelab.ringue.cloud.archiver.exception;

public class RestoreNotAvailableException extends RuntimeException {
    public RestoreNotAvailableException(String reason) {
        super("Restore not available: " + reason);
    }
}
