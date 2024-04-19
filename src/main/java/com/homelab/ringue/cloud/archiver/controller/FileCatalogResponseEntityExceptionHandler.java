package com.homelab.ringue.cloud.archiver.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

@ControllerAdvice
public class FileCatalogResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = CloudBackupException.class)
    protected ResponseEntity<Object> handleConflict(
        CloudBackupException ex, WebRequest request) {
        String bodyOfResponse = ex.getMessage();
        return handleExceptionInternal(ex, bodyOfResponse, 
          new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

}