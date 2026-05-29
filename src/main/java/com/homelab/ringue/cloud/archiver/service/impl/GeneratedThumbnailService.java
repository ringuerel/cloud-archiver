package com.homelab.ringue.cloud.archiver.service.impl;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GeneratedThumbnailService implements ThumbnailService {

    private static final String PROVIDER = "GENERATED";
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private final ApplicationProperties applicationProperties;
    private final FileCatalogItemMapper fileCatalogItemMapper;

    public GeneratedThumbnailService(
            ApplicationProperties applicationProperties,
            FileCatalogItemMapper fileCatalogItemMapper) {
        this.applicationProperties = applicationProperties;
        this.fileCatalogItemMapper = fileCatalogItemMapper;
    }

    @Override
    public FileCatalogItem createOrUpdateThumbnail(FileCatalogItem fileCatalogItem, boolean force) {
        ApplicationProperties.ThumbnailsConfig config = applicationProperties.getThumbnailsConfig();
        if (!config.isEnabled()) {
            return fileCatalogItem;
        }
        if (!force && hasThumbnail(fileCatalogItem)) {
            return fileCatalogItem;
        }
        if (!isSupportedImage(fileCatalogItem)) {
            return markSkipped(fileCatalogItem, "Unsupported media type for generated thumbnails");
        }

        Path sourcePath = Paths.get(fileCatalogItem.absolutePath());
        if (!Files.isRegularFile(sourcePath)) {
            return markFailed(fileCatalogItem, "Source file is not available on disk");
        }

        try {
            Path thumbnailPath = buildThumbnailPath(fileCatalogItem, config);
            long startTime = System.nanoTime();
            Files.createDirectories(thumbnailPath.getParent());
            createThumbnailFile(sourcePath, thumbnailPath, config.getMaxWidth(), config.getMaxHeight(), config.getOutputFormat());
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            long thumbnailSize = Files.size(thumbnailPath);
            log.info("[THUMBNAIL] Created {} ({} bytes) for {} in {} ms",
                    thumbnailPath, thumbnailSize, fileCatalogItem.absolutePath(), durationMs);
            return fileCatalogItemMapper.mapFromFileCatalogItemUpdateThumbnail(
                    fileCatalogItem,
                    thumbnailPath.toString(),
                    PROVIDER,
                    getContentType(config.getOutputFormat()),
                    Instant.now(),
                    ThumbnailStatus.CREATED.name(),
                    null);
        } catch (Exception e) {
            log.warn("Failed creating thumbnail for {}", fileCatalogItem.absolutePath(), e);
            return markFailed(fileCatalogItem, e.getMessage());
        }
    }

    @Override
    public void deleteThumbnail(FileCatalogItem fileCatalogItem) {
        if (fileCatalogItem.thumbnailPath() == null || fileCatalogItem.thumbnailPath().isBlank()) {
            return;
        }
        Path thumbnailPath = Paths.get(fileCatalogItem.thumbnailPath());
        try {
            if (Files.deleteIfExists(thumbnailPath)) {
                log.info("[THUMBNAIL] Deleted {} for {}", thumbnailPath, fileCatalogItem.absolutePath());
            } else {
                log.debug("[THUMBNAIL] Thumbnail {} for {} was already absent", thumbnailPath, fileCatalogItem.absolutePath());
            }
        } catch (IOException e) {
            log.warn("Failed deleting thumbnail {} for {}", fileCatalogItem.thumbnailPath(), fileCatalogItem.absolutePath(), e);
        }
    }

    private boolean hasThumbnail(FileCatalogItem fileCatalogItem) {
        return fileCatalogItem.thumbnailPath() != null && !fileCatalogItem.thumbnailPath().isBlank();
    }

    private boolean isSupportedImage(FileCatalogItem fileCatalogItem) {
        if (fileCatalogItem.isDirectory() || fileCatalogItem.fileExtension() == null) {
            return false;
        }
        return SUPPORTED_IMAGE_EXTENSIONS.contains(fileCatalogItem.fileExtension().toLowerCase(Locale.ROOT));
    }

    private FileCatalogItem markSkipped(FileCatalogItem fileCatalogItem, String reason) {
        return fileCatalogItemMapper.mapFromFileCatalogItemUpdateThumbnail(
                fileCatalogItem,
                null,
                PROVIDER,
                null,
                null,
                ThumbnailStatus.SKIPPED.name(),
                reason);
    }

    private FileCatalogItem markFailed(FileCatalogItem fileCatalogItem, String reason) {
        return fileCatalogItemMapper.mapFromFileCatalogItemUpdateThumbnail(
                fileCatalogItem,
                null,
                PROVIDER,
                null,
                null,
                ThumbnailStatus.FAILED.name(),
                reason);
    }

    private void createThumbnailFile(Path sourcePath, Path thumbnailPath, int maxWidth, int maxHeight, String outputFormat) throws IOException {
        BufferedImage sourceImage = ImageIO.read(sourcePath.toFile());
        if (sourceImage == null) {
            throw new IOException("File could not be decoded as an image");
        }

        double scale = Math.min((double) maxWidth / sourceImage.getWidth(), (double) maxHeight / sourceImage.getHeight());
        scale = Math.min(scale, 1.0d);
        int targetWidth = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));

        Image scaled = sourceImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = outputImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(scaled, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        if (!ImageIO.write(outputImage, outputFormat, thumbnailPath.toFile())) {
            throw new IOException("No ImageIO writer found for format " + outputFormat);
        }
    }

    private Path buildThumbnailPath(FileCatalogItem fileCatalogItem, ApplicationProperties.ThumbnailsConfig config) throws NoSuchAlgorithmException {
        String hash = sha256(fileCatalogItem.absolutePath());
        String extension = config.getOutputFormat().toLowerCase(Locale.ROOT);
        return Paths.get(resolveThumbnailRoot(fileCatalogItem, config), hash.substring(0, 2), hash.substring(2, 4), hash + "." + extension);
    }

    private String resolveThumbnailRoot(FileCatalogItem fileCatalogItem, ApplicationProperties.ThumbnailsConfig config) {
        return resolveOwningLocation(fileCatalogItem.absolutePath())
                .map(ScanLocationConfig::getThumbnailRoot)
                .filter(root -> root != null && !root.isBlank())
                .orElse(config.getLocalRoot());
    }

    private Optional<ScanLocationConfig> resolveOwningLocation(String absolutePath) {
        String normalizedAbsolutePath = absolutePath.replace('\\', '/');
        List<ScanLocationConfig> scanFolders = Optional.ofNullable(applicationProperties.getScanFolders()).orElse(List.of());
        return scanFolders.stream()
                .filter(loc -> loc.getScanFolder() != null)
                .filter(loc -> normalizedAbsolutePath.startsWith(loc.getScanFolder().replace('\\', '/')))
                .max((left, right) -> Integer.compare(left.getScanFolder().length(), right.getScanFolder().length()));
    }

    private String sha256(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String getContentType(String outputFormat) {
        return switch (outputFormat.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "image/" + outputFormat.toLowerCase(Locale.ROOT);
        };
    }
}
