package com.homelab.ringue.cloud.archiver.exception;

public class ThumbnailFailedException extends RuntimeException {
    private final String thumbnailError;

    public ThumbnailFailedException(String thumbnailError) {
        super("Thumbnail generation failed: " + thumbnailError);
        this.thumbnailError = thumbnailError;
    }

    public String getThumbnailError() {
        return thumbnailError;
    }
}
