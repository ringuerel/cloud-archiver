package com.homelab.ringue.cloud.archiver.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseRequest;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.MediaStatusResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreResponse;
import com.homelab.ringue.cloud.archiver.exception.InvalidLimitException;
import com.homelab.ringue.cloud.archiver.exception.MissingPathException;
import com.homelab.ringue.cloud.archiver.service.GalleryService;
import com.homelab.ringue.cloud.archiver.service.MediaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GalleryController.class)
class GalleryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GalleryService galleryService;

    @MockBean
    private MediaService mediaService;

    @Test
    void browse_withMissingParametersUsesDefaults() throws Exception {
        when(galleryService.browse(any())).thenReturn(new BrowseResponse(List.of(), null, false));

        mockMvc.perform(get("/file-catalog/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false));

        ArgumentCaptor<BrowseRequest> captor = ArgumentCaptor.forClass(BrowseRequest.class);
        verify(galleryService).browse(captor.capture());
        assertEquals(60, captor.getValue().limit());
        assertEquals(false, captor.getValue().includeDirectories());
    }

    @Test
    void thumbnail_delegatesAndReturnsServiceStatusAndHeaders() throws Exception {
        ResponseEntity<?> response = ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"2-3\"")
                .body(new ByteArrayResource("thumb".getBytes()));
        doReturn(response).when(mediaService).streamThumbnail(eq("/media/photo.jpg"), eq("\"1-2\""));

        mockMvc.perform(get("/file-catalog/media/thumbnail")
                        .param("path", "/media/photo.jpg")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"1-2\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2-3\""));
    }

    @Test
    void original_delegatesAndReturnsAcceptedForRestoreInProgress() throws Exception {
        ResponseEntity<?> response = ResponseEntity.accepted().body(new RestoreResponse(
                "ALREADY_IN_PROGRESS", "job-1", "/media/photo.jpg", null, "C:/downloads"));
        doReturn(response).when(mediaService).streamOriginal(eq("/media/photo.jpg"), eq(null));

        mockMvc.perform(get("/file-catalog/media/original")
                        .param("path", "/media/photo.jpg"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ALREADY_IN_PROGRESS"));
    }

    @Test
    void status_delegatesAndReturnsJson() throws Exception {
        MediaStatusResponse response = new MediaStatusResponse(
                new MediaStatusResponse.ThumbnailStatus(false, null, null, null, null, null),
                new MediaStatusResponse.OriginalStatus(false, null, null, null, null),
                new MediaStatusResponse.RestoreStatus(true, false, null));
        when(mediaService.getStatus("/media/photo.jpg")).thenReturn(response);

        mockMvc.perform(get("/file-catalog/media/status")
                        .param("path", "/media/photo.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restore.available").value(true));
    }

    @Test
    void restore_returnsAcceptedForQueued() throws Exception {
        when(mediaService.restore("/media/photo.jpg")).thenReturn(new RestoreResponse(
                "QUEUED", "job-1", "/media/photo.jpg", null, "C:/downloads"));

        mockMvc.perform(post("/file-catalog/media/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/media/photo.jpg\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void restore_returnsOkForAlreadyAvailable() throws Exception {
        when(mediaService.restore("/media/photo.jpg")).thenReturn(new RestoreResponse(
                "ALREADY_AVAILABLE", null, "/media/photo.jpg", "/file-catalog/media/original?path=%2Fmedia%2Fphoto.jpg", null));

        mockMvc.perform(post("/file-catalog/media/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/media/photo.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_AVAILABLE"));
    }

    @Test
    void galleryException_returnsJsonErrorBody() throws Exception {
        when(galleryService.browse(any())).thenThrow(new InvalidLimitException(201));

        mockMvc.perform(get("/file-catalog/browse").param("limit", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_LIMIT"));
    }

    @Test
    void missingPathException_returnsJsonErrorBody() throws Exception {
        when(mediaService.getStatus("")).thenThrow(new MissingPathException());

        mockMvc.perform(get("/file-catalog/media/status").param("path", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("MISSING_PATH"));
    }
}
