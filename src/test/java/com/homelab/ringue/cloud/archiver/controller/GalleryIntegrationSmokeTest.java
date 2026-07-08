package com.homelab.ringue.cloud.archiver.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homelab.ringue.cloud.archiver.gallery.ContentSecurityGuard;
import com.homelab.ringue.cloud.archiver.gallery.RestoreJobRegistry;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository;
import com.homelab.ringue.cloud.archiver.service.impl.GalleryServiceImpl;
import com.homelab.ringue.cloud.archiver.service.impl.MediaServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.data.mongodb.database=gallery_integration_smoke_test",
        "de.flapdoodle.mongodb.embedded.version=7.0.5"
})
@AutoConfigureMockMvc
class GalleryIntegrationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GalleryController galleryController;

    @Autowired
    private GalleryServiceImpl galleryService;

    @Autowired
    private MediaServiceImpl mediaService;

    @Autowired
    private ContentSecurityGuard contentSecurityGuard;

    @Autowired
    private RestoreJobRegistry restoreJobRegistry;

    @Autowired
    private GalleryBrowseRepository galleryBrowseRepository;

    @Autowired
    private GalleryResponseEntityExceptionHandler galleryExceptionHandler;

    @Test
    void galleryBeansAreRegisteredAndBrowseReturnsOk() throws Exception {
        assertNotNull(galleryController);
        assertNotNull(galleryService);
        assertNotNull(mediaService);
        assertNotNull(contentSecurityGuard);
        assertNotNull(restoreJobRegistry);
        assertNotNull(galleryBrowseRepository);
        assertNotNull(galleryExceptionHandler);

        mockMvc.perform(get("/file-catalog/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false));
    }
}
