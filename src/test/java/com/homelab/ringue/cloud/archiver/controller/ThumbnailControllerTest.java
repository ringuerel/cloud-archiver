package com.homelab.ringue.cloud.archiver.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildMode;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailRebuildSummary;
import com.homelab.ringue.cloud.archiver.service.ThumbnailRebuildService;

@WebMvcTest(ThumbnailController.class)
class ThumbnailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ThumbnailRebuildService thumbnailRebuildService;

    @Test
    void rebuildThumbnails_defaultMode_returnsSummary() throws Exception {
        ThumbnailRebuildSummary summary = new ThumbnailRebuildSummary(ThumbnailRebuildMode.MISSING_ONLY, 10, 8, 1, 1);
        when(thumbnailRebuildService.rebuildThumbnails(
                any(ThumbnailRebuildMode.class),
                any(Optional.class),
                any(Optional.class),
                any(Optional.class),
                any(Optional.class)))
                .thenReturn(summary);

        mockMvc.perform(post("/thumbnails/rebuild")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MISSING_ONLY"))
                .andExpect(jsonPath("$.processedCount").value(10))
                .andExpect(jsonPath("$.createdCount").value(8))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test
    void rebuildThumbnails_withAllParams_delegatesCorrectly() throws Exception {
        ThumbnailRebuildSummary summary = new ThumbnailRebuildSummary(ThumbnailRebuildMode.FORCE, 3, 3, 0, 0);
        when(thumbnailRebuildService.rebuildThumbnails(
                any(ThumbnailRebuildMode.class),
                any(Optional.class),
                any(Optional.class),
                any(Optional.class),
                any(Optional.class)))
                .thenReturn(summary);

        mockMvc.perform(post("/thumbnails/rebuild")
                        .param("mode", "FORCE")
                        .param("path", "/immich/library")
                        .param("fileNameContains", "photo")
                        .param("limit", "100")
                        .param("concurrency", "4")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FORCE"))
                .andExpect(jsonPath("$.processedCount").value(3))
                .andExpect(jsonPath("$.createdCount").value(3));

        verify(thumbnailRebuildService).rebuildThumbnails(
                ThumbnailRebuildMode.FORCE,
                Optional.of("/immich/library"),
                Optional.of("photo"),
                Optional.of(100),
                Optional.of(4));
    }

    @Test
    void rebuildThumbnails_failedOnlyMode_returnsSummary() throws Exception {
        ThumbnailRebuildSummary summary = new ThumbnailRebuildSummary(ThumbnailRebuildMode.FAILED_ONLY, 5, 4, 0, 1);
        when(thumbnailRebuildService.rebuildThumbnails(
                any(ThumbnailRebuildMode.class),
                any(Optional.class),
                any(Optional.class),
                any(Optional.class),
                any(Optional.class)))
                .thenReturn(summary);

        mockMvc.perform(post("/thumbnails/rebuild")
                        .param("mode", "FAILED_ONLY")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FAILED_ONLY"))
                .andExpect(jsonPath("$.failedCount").value(1));

        verify(thumbnailRebuildService).rebuildThumbnails(
                ThumbnailRebuildMode.FAILED_ONLY,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
