package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.domain.gallery.MediaStatusResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface MediaService {
    ResponseEntity<StreamingResponseBody> streamThumbnail(String absolutePath, String ifNoneMatch);

    ResponseEntity<?> streamOriginal(String absolutePath, String ifNoneMatch);

    MediaStatusResponse getStatus(String absolutePath);

    RestoreResponse restore(String absolutePath);

    static String computeETag(long size, long lastModifiedMs) {
        return "\"" + size + "-" + lastModifiedMs + "\"";
    }
}
