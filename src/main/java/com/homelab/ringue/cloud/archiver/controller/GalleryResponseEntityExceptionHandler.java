package com.homelab.ringue.cloud.archiver.controller;

import com.homelab.ringue.cloud.archiver.domain.gallery.GalleryErrorResponse;
import com.homelab.ringue.cloud.archiver.exception.GalleryAccessDeniedException;
import com.homelab.ringue.cloud.archiver.exception.GalleryItemNotFoundException;
import com.homelab.ringue.cloud.archiver.exception.InvalidCursorException;
import com.homelab.ringue.cloud.archiver.exception.InvalidDateFormatException;
import com.homelab.ringue.cloud.archiver.exception.InvalidLimitException;
import com.homelab.ringue.cloud.archiver.exception.MissingPathException;
import com.homelab.ringue.cloud.archiver.exception.RestoreNotAvailableException;
import com.homelab.ringue.cloud.archiver.exception.ThumbnailFailedException;
import com.homelab.ringue.cloud.archiver.exception.ThumbnailSkippedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GalleryResponseEntityExceptionHandler extends FileCatalogResponseEntityExceptionHandler {

    @ExceptionHandler(GalleryItemNotFoundException.class)
    public ResponseEntity<GalleryErrorResponse> handleGalleryItemNotFound(GalleryItemNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(ThumbnailFailedException.class)
    public ResponseEntity<GalleryErrorResponse> handleThumbnailFailed(ThumbnailFailedException exception) {
        return error(HttpStatus.CONFLICT, "THUMBNAIL_FAILED", exception.getThumbnailError());
    }

    @ExceptionHandler(ThumbnailSkippedException.class)
    public ResponseEntity<GalleryErrorResponse> handleThumbnailSkipped(ThumbnailSkippedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "THUMBNAIL_SKIPPED", exception.getMessage());
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<GalleryErrorResponse> handleInvalidCursor(InvalidCursorException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception.getMessage());
    }

    @ExceptionHandler(InvalidLimitException.class)
    public ResponseEntity<GalleryErrorResponse> handleInvalidLimit(InvalidLimitException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", exception.getMessage());
    }

    @ExceptionHandler(InvalidDateFormatException.class)
    public ResponseEntity<GalleryErrorResponse> handleInvalidDateFormat(InvalidDateFormatException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT", exception.getMessage());
    }

    @ExceptionHandler(MissingPathException.class)
    public ResponseEntity<GalleryErrorResponse> handleMissingPath(MissingPathException exception) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PATH", exception.getMessage());
    }

    @ExceptionHandler(RestoreNotAvailableException.class)
    public ResponseEntity<GalleryErrorResponse> handleRestoreNotAvailable(RestoreNotAvailableException exception) {
        return error(HttpStatus.CONFLICT, "RESTORE_NOT_AVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(GalleryAccessDeniedException.class)
    public ResponseEntity<GalleryErrorResponse> handleAccessDenied(GalleryAccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", exception.getMessage());
    }

    private ResponseEntity<GalleryErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GalleryErrorResponse(code, message));
    }
}
