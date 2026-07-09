package com.homelab.ringue.cloud.archiver.exception;

public class ThumbnailSkippedException extends RuntimeException {
    public ThumbnailSkippedException(String path) {
        super("Thumbnail was skipped for: " + path);
    }
}
