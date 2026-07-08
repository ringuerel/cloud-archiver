package com.homelab.ringue.cloud.archiver.exception;

public class GalleryItemNotFoundException extends RuntimeException {
    private final String errorCode;

    public GalleryItemNotFoundException(String errorCode, String path) {
        super("Gallery item not found [" + errorCode + "]: " + path);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
