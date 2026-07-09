package com.homelab.ringue.cloud.archiver.exception;

public class GalleryAccessDeniedException extends RuntimeException {
    public GalleryAccessDeniedException(String resolvedPath) {
        super("Access denied: path is outside all allowed roots: " + resolvedPath);
    }
}
