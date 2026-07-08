package com.homelab.ringue.cloud.archiver.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.gallery.RestoreJobRegistry;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository;
import com.homelab.ringue.cloud.archiver.service.MediaService;
import com.homelab.ringue.cloud.archiver.service.impl.GalleryServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@WebMvcTest(GalleryController.class)
@Import({
        GalleryIntegrationSmokeTest.GallerySmokeTestConfiguration.class,
        GalleryResponseEntityExceptionHandler.class
})
class GalleryIntegrationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GalleryController galleryController;

    @Autowired
    private GalleryServiceImpl galleryService;

    @MockBean
    private MediaService mediaService;

    @Autowired
    private RestoreJobRegistry restoreJobRegistry;

    @Autowired
    private GalleryBrowseRepository galleryBrowseRepository;

    @Autowired
    private GalleryResponseEntityExceptionHandler galleryExceptionHandler;

    @Test
    void galleryBeansAreRegisteredAndBrowseReturnsOk() throws Exception {
        when(mongoTemplate.find(any(Query.class), eq(FileCatalogItem.class))).thenReturn(List.of());

        assertNotNull(galleryController);
        assertNotNull(galleryService);
        assertNotNull(mediaService);
        assertNotNull(restoreJobRegistry);
        assertNotNull(galleryBrowseRepository);
        assertNotNull(galleryExceptionHandler);

        mockMvc.perform(get("/file-catalog/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @MockBean
    private MongoTemplate mongoTemplate;

    @TestConfiguration
    static class GallerySmokeTestConfiguration {

        @Bean
        RestoreJobRegistry restoreJobRegistry() {
            return new RestoreJobRegistry();
        }

        @Bean
        GalleryBrowseRepository galleryBrowseRepository(MongoTemplate mongoTemplate) {
            return new GalleryBrowseRepository(mongoTemplate);
        }

        @Bean
        GalleryServiceImpl galleryService(GalleryBrowseRepository galleryBrowseRepository,
                RestoreJobRegistry restoreJobRegistry) {
            return new GalleryServiceImpl(galleryBrowseRepository, restoreJobRegistry);
        }
    }
}
