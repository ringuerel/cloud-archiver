package com.homelab.ringue.cloud.archiver.exception;

public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}
