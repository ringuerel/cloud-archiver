package com.homelab.ringue.cloud.archiver.controller;

import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseRequest;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.MediaStatusResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreRequest;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreResponse;
import com.homelab.ringue.cloud.archiver.service.GalleryService;
import com.homelab.ringue.cloud.archiver.service.MediaService;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("file-catalog")
@Scope("prototype")
public class GalleryController {

    private final GalleryService galleryService;
    private final MediaService mediaService;

    public GalleryController(GalleryService galleryService, MediaService mediaService) {
        this.galleryService = galleryService;
        this.mediaService = mediaService;
    }

    @GetMapping("/browse")
    public ResponseEntity<BrowseResponse> browse(
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "60") int limit,
            @RequestParam(required = false) String fileNameContains,
            @RequestParam(required = false) String extensions,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String thumbnailStatus,
            @RequestParam(defaultValue = "false") boolean includeDirectories) {
        BrowseRequest request = new BrowseRequest(path, cursor, limit, fileNameContains, extensions, startDate,
                endDate, thumbnailStatus, includeDirectories);
        return ResponseEntity.ok(galleryService.browse(request));
    }

    @GetMapping("/media/thumbnail")
    public ResponseEntity<StreamingResponseBody> streamThumbnail(
            @RequestParam String path,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return mediaService.streamThumbnail(path, ifNoneMatch);
    }

    @GetMapping("/media/original")
    public ResponseEntity<?> streamOriginal(
            @RequestParam String path,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return mediaService.streamOriginal(path, ifNoneMatch);
    }

    @GetMapping("/media/status")
    public ResponseEntity<MediaStatusResponse> getStatus(@RequestParam String path) {
        return ResponseEntity.ok(mediaService.getStatus(path));
    }

    @PostMapping("/media/restore")
    public ResponseEntity<RestoreResponse> restore(@RequestBody RestoreRequest request) {
        RestoreResponse response = mediaService.restore(request.path());
        HttpStatus status = "ALREADY_AVAILABLE".equals(response.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

}
