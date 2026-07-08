package com.homelab.ringue.cloud.archiver.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ThumbnailsConfig;
import com.homelab.ringue.cloud.archiver.exception.GalleryAccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentSecurityGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void validateAndResolve_forPathUnderScanFolder_returnsResolvedPath() throws Exception {
        Path scanRoot = Files.createDirectories(tempDir.resolve("scan"));
        Path file = Files.writeString(scanRoot.resolve("photo.jpg"), "image");
        ContentSecurityGuard guard = guardWith(scanRoot, null, null);

        Path resolved = guard.validateAndResolve(file.toString());

        assertEquals(file.toRealPath(), resolved);
    }

    @Test
    void validateAndResolve_forPathUnderThumbnailRoot_returnsResolvedPath() throws Exception {
        Path scanRoot = Files.createDirectories(tempDir.resolve("scan"));
        Path thumbnailRoot = Files.createDirectories(tempDir.resolve("thumbs"));
        Path thumbnail = Files.writeString(thumbnailRoot.resolve("photo.jpg"), "thumbnail");
        ContentSecurityGuard guard = guardWith(scanRoot, thumbnailRoot, null);

        Path resolved = guard.validateAndResolve(thumbnail.toString());

        assertEquals(thumbnail.toRealPath(), resolved);
    }

    @Test
    void validateAndResolve_forPathOutsideAllowedRoots_throwsAccessDenied() throws Exception {
        Path scanRoot = Files.createDirectories(tempDir.resolve("scan"));
        Path outside = Files.writeString(tempDir.resolve("outside.jpg"), "image");
        ContentSecurityGuard guard = guardWith(scanRoot, null, null);

        assertThrows(GalleryAccessDeniedException.class, () -> guard.validateAndResolve(outside.toString()));
    }

    @Test
    void initializeAllowedRoots_whenThumbnailRootDoesNotExist_excludesRootWithoutFailure() throws Exception {
        Path scanRoot = Files.createDirectories(tempDir.resolve("scan"));
        Path missingThumbnailRoot = tempDir.resolve("missing-thumbs");
        ContentSecurityGuard guard = guardWith(scanRoot, missingThumbnailRoot, null);

        assertFalse(guard.allowedRoots().contains(missingThumbnailRoot.normalize().toAbsolutePath()));
        assertThrows(GalleryAccessDeniedException.class,
                () -> guard.validateAndResolve(missingThumbnailRoot.resolve("photo.jpg").toString()));
    }

    private ContentSecurityGuard guardWith(Path scanRoot, Path thumbnailRoot, Path globalThumbnailRoot) throws Exception {
        ApplicationProperties properties = new ApplicationProperties();

        ThumbnailsConfig thumbnailsConfig = new ThumbnailsConfig();
        Path resolvedGlobalThumbnailRoot = globalThumbnailRoot == null
                ? Files.createDirectories(tempDir.resolve("global-thumbs"))
                : globalThumbnailRoot;
        thumbnailsConfig.setLocalRoot(resolvedGlobalThumbnailRoot.toString());
        properties.setThumbnailsConfig(thumbnailsConfig);

        ScanLocationConfig scanLocation = new ScanLocationConfig();
        scanLocation.setScanFolder(scanRoot.toString());
        if (thumbnailRoot != null) {
            scanLocation.setThumbnailRoot(thumbnailRoot.toString());
        }
        properties.setScanFolders(List.of(scanLocation));

        ContentSecurityGuard guard = new ContentSecurityGuard(properties);
        guard.initializeAllowedRoots();
        return guard;
    }
}
