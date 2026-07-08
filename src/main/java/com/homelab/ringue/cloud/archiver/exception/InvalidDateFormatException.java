package com.homelab.ringue.cloud.archiver.exception;

public class InvalidDateFormatException extends RuntimeException {
    public InvalidDateFormatException(String paramName, String value) {
        super("Invalid ISO-8601 date format for parameter '" + paramName + "': " + value);
    }
}
