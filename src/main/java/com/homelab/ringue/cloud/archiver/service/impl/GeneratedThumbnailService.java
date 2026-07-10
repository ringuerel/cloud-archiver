package com.homelab.ringue.cloud.archiver.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.ThumbnailProcessRunner;
import com.homelab.ringue.cloud.archiver.service.ThumbnailService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GeneratedThumbnailService implements ThumbnailService {

    private static final String PROVIDER = "GENERATED";
    private static final double DEFAULT_VIDEO_CAPTURE_AT_SECONDS = 3.0d;
    private static final double MIN_VIDEO_CAPTURE_AT_SECONDS = 0.1d;
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final Set<String> SUPPORTED_HEIC_EXTENSIONS = Set.of("heic", "heif");
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = Set.of("mp4", "mov", "m4v");

    private final ApplicationProperties applicationProperties;
    private final FileCatalogItemMapper fileCatalogItemMapper;
    private final ThumbnailProcessRunner thumbnailProcessRunner;

    public GeneratedThumbnailService(
            ApplicationProperties applicationProperties,
            FileCatalogItemMapper fileCatalogItemMapper,
            ThumbnailProcessRunner thumbnailProcessRunner) {
        this.applicationProperties = applicationProperties;
        this.fileCatalogItemMapper = fileCatalogItemMapper;
        this.thumbnailProcessRunner = thumbnailProcessRunner;
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
        MediaType mediaType = mediaType(fileCatalogItem);
        if (mediaType == MediaType.UNSUPPORTED) {
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
            createThumbnailFile(sourcePath, thumbnailPath, config, mediaType);
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted creating thumbnail for {}", fileCatalogItem.absolutePath(), e);
            return markFailed(fileCatalogItem, "Thumbnail generation was interrupted");
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

    private MediaType mediaType(FileCatalogItem fileCatalogItem) {
        if (fileCatalogItem.isDirectory() || fileCatalogItem.fileExtension() == null) {
            return MediaType.UNSUPPORTED;
        }
        String extension = fileCatalogItem.fileExtension().toLowerCase(Locale.ROOT);
        if (SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            return MediaType.IMAGE;
        }
        if (SUPPORTED_HEIC_EXTENSIONS.contains(extension)) {
            return MediaType.HEIC;
        }
        if (SUPPORTED_VIDEO_EXTENSIONS.contains(extension)) {
            return MediaType.VIDEO;
        }
        return MediaType.UNSUPPORTED;
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

    private void createThumbnailFile(
            Path sourcePath,
            Path thumbnailPath,
            ApplicationProperties.ThumbnailsConfig config,
            MediaType mediaType) throws IOException, InterruptedException {
        Path ffmpegInputPath = sourcePath;
        Path heicTempPath = null;
        try {
            if (mediaType == MediaType.HEIC) {
                heicTempPath = Files.createTempFile(thumbnailPath.getParent(), "heic-source-", ".png");
                try {
                    runCommand(List.of(
                            config.getHeifConvertPath(),
                            sourcePath.toString(),
                            heicTempPath.toString()), config);
                    ffmpegInputPath = heicTempPath;
                } catch (IOException e) {
                    log.debug("heif-convert failed for {}; falling back to ffmpeg direct decode", sourcePath, e);
                    Files.deleteIfExists(heicTempPath);
                    heicTempPath = null;
                }
            }

            runFfmpegThumbnailCommand(ffmpegInputPath, thumbnailPath, config, mediaType);
            if (!Files.isRegularFile(thumbnailPath)) {
                throw new IOException("Thumbnail command completed without creating output file");
            }
        } finally {
            if (heicTempPath != null) {
                Files.deleteIfExists(heicTempPath);
            }
        }
    }

    private List<String> buildFfmpegCommand(
            Path sourcePath,
            Path thumbnailPath,
            ApplicationProperties.ThumbnailsConfig config,
            Optional<Double> captureAtSeconds) {
        List<String> command = new ArrayList<>();
        command.add(config.getFfmpegPath());
        command.add("-y");
        if (captureAtSeconds.isPresent()) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.3f", captureAtSeconds.get()));
        }
        command.add("-i");
        command.add(sourcePath.toString());
        command.add("-frames:v");
        command.add("1");
        command.add("-filter_complex");
        command.add("[0:v]" + scaleFilter(config.getMaxWidth(), config.getMaxHeight()) + "[thumb]");
        command.add("-map");
        command.add("[thumb]");
        command.add(thumbnailPath.toString());
        return command;
    }

    private void runFfmpegThumbnailCommand(
            Path sourcePath,
            Path thumbnailPath,
            ApplicationProperties.ThumbnailsConfig config,
            MediaType mediaType) throws IOException, InterruptedException {
        boolean video = mediaType == MediaType.VIDEO;
        Optional<Double> captureAtSeconds = video ? resolveVideoCaptureAtSeconds(sourcePath, config) : Optional.empty();
        try {
            runCommand(buildFfmpegCommand(sourcePath, thumbnailPath, config, captureAtSeconds), config);
        } catch (IOException e) {
            if (!video) {
                throw e;
            }
            log.debug("ffmpeg video thumbnail capture failed for {}; falling back to first frame", sourcePath, e);
            Files.deleteIfExists(thumbnailPath);
            runCommand(buildFfmpegCommand(sourcePath, thumbnailPath, config, Optional.empty()), config);
        }
    }

    private Optional<Double> resolveVideoCaptureAtSeconds(
            Path sourcePath,
            ApplicationProperties.ThumbnailsConfig config) throws InterruptedException {
        try {
            ThumbnailProcessRunner.ProcessResult result = thumbnailProcessRunner.run(
                    List.of(
                            config.getFfprobePath(),
                            "-v",
                            "error",
                            "-show_entries",
                            "format=duration",
                            "-of",
                            "default=noprint_wrappers=1:nokey=1",
                            sourcePath.toString()),
                    Duration.ofSeconds(config.getCommandTimeoutSeconds()));
            if (result.exitCode() != 0) {
                log.debug("ffprobe failed for {}; using first frame fallback: {}", sourcePath, result.output());
                return Optional.empty();
            }

            double durationSeconds = Double.parseDouble(result.output().strip());
            if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0d) {
                return Optional.empty();
            }

            double captureAtSeconds = Math.min(DEFAULT_VIDEO_CAPTURE_AT_SECONDS, durationSeconds / 2.0d);
            return Optional.of(Math.max(MIN_VIDEO_CAPTURE_AT_SECONDS, captureAtSeconds));
        } catch (NumberFormatException | IOException e) {
            log.debug("Unable to determine video duration for {}; using first frame fallback", sourcePath, e);
            return Optional.empty();
        }
    }

    private String scaleFilter(int maxWidth, int maxHeight) {
        return "scale='min(" + maxWidth + ",iw)':'min(" + maxHeight + ",ih)':force_original_aspect_ratio=decrease";
    }

    private void runCommand(List<String> command, ApplicationProperties.ThumbnailsConfig config) throws IOException, InterruptedException {
        ThumbnailProcessRunner.ProcessResult result = thumbnailProcessRunner.run(
                command,
                Duration.ofSeconds(config.getCommandTimeoutSeconds()));
        if (result.exitCode() != 0) {
            throw new IOException("Thumbnail command failed with exit code " + result.exitCode() + ": " + result.output());
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

    private enum MediaType {
        IMAGE,
        HEIC,
        VIDEO,
        UNSUPPORTED
    }
}
