package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ThumbnailsConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;

class GeneratedThumbnailServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createOrUpdateThumbnail_whenDisabled_returnsOriginalItem() throws Exception {
        ApplicationProperties properties = properties(false);
        FileCatalogItem item = catalogItem(writeImage("photo.jpg"));
        GeneratedThumbnailService service = new GeneratedThumbnailService(
                properties,
                new FileCatalogItemMapperImpl());

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertSame(item, result);
    }

    @Test
    void createOrUpdateThumbnail_forImage_uploadsThumbnailAndUpdatesCatalogMetadata() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeImage("photo.jpg"));
        GeneratedThumbnailService service = new GeneratedThumbnailService(
                properties,
                new FileCatalogItemMapperImpl());

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals("GENERATED", result.thumbnailProvider());
        assertEquals("image/jpeg", result.thumbnailContentType());
        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertNotNull(result.thumbnailCreatedAt());
        assertNull(result.thumbnailError());
        assertTrue(result.thumbnailPath().startsWith(tempDir.resolve("thumbs").toString()));
        assertTrue(java.nio.file.Files.isRegularFile(Path.of(result.thumbnailPath())));
    }

    @Test
    void createOrUpdateThumbnail_usesScanLocationThumbnailRootWhenConfigured() throws Exception {
        ApplicationProperties properties = properties(true);
        Path locationThumbRoot = tempDir.resolve("location-thumbs");
        ScanLocationConfig locationConfig = new ScanLocationConfig();
        locationConfig.setScanFolder(tempDir.toString());
        locationConfig.setThumbnailRoot(locationThumbRoot.toString());
        properties.setScanFolders(List.of(locationConfig));
        FileCatalogItem item = catalogItem(writeImage("photo.jpg"));
        GeneratedThumbnailService service = new GeneratedThumbnailService(
                properties,
                new FileCatalogItemMapperImpl());

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertTrue(result.thumbnailPath().startsWith(locationThumbRoot.toString()));
        assertTrue(java.nio.file.Files.isRegularFile(Path.of(result.thumbnailPath())));
    }

    @Test
    void createOrUpdateThumbnail_forUnsupportedFile_marksSkippedWithoutPath() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = new FileCatalogItem(
                tempDir.resolve("notes.txt").toString(),
                "notes.txt",
                "txt",
                tempDir.toString(),
                false,
                10L,
                new Date(),
                "crc",
                Instant.now());
        GeneratedThumbnailService service = new GeneratedThumbnailService(
                properties,
                new FileCatalogItemMapperImpl());

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertNull(result.thumbnailPath());
        assertEquals(ThumbnailStatus.SKIPPED.name(), result.thumbnailStatus());
    }

    private ApplicationProperties properties(boolean thumbnailsEnabled) {
        ApplicationProperties properties = new ApplicationProperties();

        ThumbnailsConfig thumbnailsConfig = new ThumbnailsConfig();
        thumbnailsConfig.setEnabled(thumbnailsEnabled);
        thumbnailsConfig.setLocalRoot(tempDir.resolve("thumbs").toString());
        thumbnailsConfig.setMaxWidth(64);
        thumbnailsConfig.setMaxHeight(64);
        thumbnailsConfig.setOutputFormat("jpg");
        properties.setThumbnailsConfig(thumbnailsConfig);
        return properties;
    }

    private FileCatalogItem catalogItem(Path imagePath) throws Exception {
        return new FileCatalogItem(
                imagePath.toString(),
                imagePath.getFileName().toString(),
                "jpg",
                imagePath.getParent().toString(),
                false,
                java.nio.file.Files.size(imagePath),
                new Date(),
                "crc",
                Instant.now());
    }

    private Path writeImage(String fileName) throws Exception {
        Path imagePath = tempDir.resolve(fileName);
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "jpg", imagePath.toFile());
        return imagePath;
    }
}
