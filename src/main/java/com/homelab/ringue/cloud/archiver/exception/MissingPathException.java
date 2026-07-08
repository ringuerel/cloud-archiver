package com.homelab.ringue.cloud.archiver.exception;

public class MissingPathException extends RuntimeException {
    public MissingPathException() {
        super("Required parameter 'path' is absent or blank");
    }
}
