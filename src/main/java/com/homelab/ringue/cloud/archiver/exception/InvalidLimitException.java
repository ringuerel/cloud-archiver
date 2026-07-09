package com.homelab.ringue.cloud.archiver.exception;

public class InvalidLimitException extends RuntimeException {
    public InvalidLimitException(int limit) {
        super("Limit exceeds maximum allowed value of 200: " + limit);
    }
}
