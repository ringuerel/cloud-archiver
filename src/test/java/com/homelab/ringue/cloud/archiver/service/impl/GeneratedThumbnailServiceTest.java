package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ThumbnailsConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.service.ThumbnailProcessRunner;

class GeneratedThumbnailServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createOrUpdateThumbnail_whenDisabled_returnsOriginalItem() throws Exception {
        ApplicationProperties properties = properties(false);
        FileCatalogItem item = catalogItem(writeSource("photo.jpg"), "jpg");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertSame(item, result);
        assertTrue(runner.commands.isEmpty());
    }

    @Test
    void createOrUpdateThumbnail_forImage_invokesFfmpegAndUpdatesCatalogMetadata() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("photo.jpg"), "jpg");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals("GENERATED", result.thumbnailProvider());
        assertEquals("image/jpeg", result.thumbnailContentType());
        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertNotNull(result.thumbnailCreatedAt());
        assertNull(result.thumbnailError());
        assertTrue(result.thumbnailPath().startsWith(tempDir.resolve("thumbs").toString()));
        assertTrue(Files.isRegularFile(Path.of(result.thumbnailPath())));
        assertEquals(1, runner.commands.size());
        assertEquals("ffmpeg", runner.commands.get(0).get(0));
        assertTrue(runner.commands.get(0).contains(item.absolutePath()));
        assertTrue(runner.commands.get(0).contains("-filter_complex"));
        assertTrue(runner.commands.get(0).contains("-map"));
    }

    @Test
    void createOrUpdateThumbnail_forHeic_invokesHeifConvertThenFfmpeg() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("photo.heic"), "heic");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertTrue(Files.isRegularFile(Path.of(result.thumbnailPath())));
        assertEquals(2, runner.commands.size());
        assertEquals("heif-convert", runner.commands.get(0).get(0));
        assertEquals(item.absolutePath(), runner.commands.get(0).get(1));
        assertEquals("ffmpeg", runner.commands.get(1).get(0));
    }

    @Test
    void createOrUpdateThumbnail_forHeicWhenHeifConvertFails_fallsBackToFfmpegDirectDecode() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("photo.heic"), "heic");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(1, "Possibly could be a JPEG file instead"));
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, ""));
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertTrue(Files.isRegularFile(Path.of(result.thumbnailPath())));
        assertEquals(2, runner.commands.size());
        assertEquals("heif-convert", runner.commands.get(0).get(0));
        assertEquals("ffmpeg", runner.commands.get(1).get(0));
        assertTrue(runner.commands.get(1).contains(item.absolutePath()));
    }

    @Test
    void createOrUpdateThumbnail_forVideo_invokesFfmpegFrameExtraction() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("clip.mov"), "mov");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, "12.000000"));
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, ""));
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertEquals(2, runner.commands.size());
        assertEquals("ffprobe", runner.commands.get(0).get(0));
        List<String> command = runner.commands.get(1);
        assertEquals("ffmpeg", command.get(0));
        assertTrue(command.contains("-ss"));
        assertTrue(command.contains("3.000"));
        assertTrue(command.contains(item.absolutePath()));
    }

    @Test
    void createOrUpdateThumbnail_forShortVideo_usesTimestampWithinDuration() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("short.mp4"), "mp4");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, "2.970000"));
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, ""));
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertTrue(Files.isRegularFile(Path.of(result.thumbnailPath())));
        assertEquals(2, runner.commands.size());
        assertEquals("ffprobe", runner.commands.get(0).get(0));
        assertTrue(runner.commands.get(1).contains("-ss"));
        assertTrue(runner.commands.get(1).contains("1.485"));
        assertTrue(runner.commands.get(1).contains(item.absolutePath()));
    }

    @Test
    void createOrUpdateThumbnail_forVideoWhenDurationProbeFails_fallsBackToFirstFrame() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("probe-fails.mp4"), "mp4");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(1, "probe failed"));
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, ""));
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertEquals(2, runner.commands.size());
        assertEquals("ffprobe", runner.commands.get(0).get(0));
        assertEquals("ffmpeg", runner.commands.get(1).get(0));
        assertFalse(runner.commands.get(1).contains("-ss"));
    }

    @Test
    void createOrUpdateThumbnail_forVideoWhenCaptureFails_retriesFirstFrame() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("capture-fails.mp4"), "mp4");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, "12.000000"));
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(234, "Nothing was written into output file"));
        runner.commandResults.add(new ThumbnailProcessRunner.ProcessResult(0, ""));
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertEquals(3, runner.commands.size());
        assertTrue(runner.commands.get(1).contains("-ss"));
        assertFalse(runner.commands.get(2).contains("-ss"));
    }

    @Test
    void createOrUpdateThumbnail_usesScanLocationThumbnailRootWhenConfigured() throws Exception {
        ApplicationProperties properties = properties(true);
        Path locationThumbRoot = tempDir.resolve("location-thumbs");
        ScanLocationConfig locationConfig = new ScanLocationConfig();
        locationConfig.setScanFolder(tempDir.toString());
        locationConfig.setThumbnailRoot(locationThumbRoot.toString());
        properties.setScanFolders(List.of(locationConfig));
        FileCatalogItem item = catalogItem(writeSource("photo.webp"), "webp");
        GeneratedThumbnailService service = service(properties, new FakeThumbnailProcessRunner());

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertTrue(result.thumbnailPath().startsWith(locationThumbRoot.toString()));
        assertTrue(Files.isRegularFile(Path.of(result.thumbnailPath())));
    }

    @Test
    void createOrUpdateThumbnail_forUnsupportedFile_marksSkippedWithoutPath() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("notes.txt"), "txt");
        GeneratedThumbnailService service = service(properties, new FakeThumbnailProcessRunner());

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertNull(result.thumbnailPath());
        assertEquals(ThumbnailStatus.SKIPPED.name(), result.thumbnailStatus());
    }

    @Test
    void createOrUpdateThumbnail_whenCommandFails_marksFailedWithoutThrowing() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("photo.jpg"), "jpg");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.result = new ThumbnailProcessRunner.ProcessResult(1, "decode failed");
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertNull(result.thumbnailPath());
        assertEquals(ThumbnailStatus.FAILED.name(), result.thumbnailStatus());
        assertTrue(result.thumbnailError().contains("decode failed"));
    }

    @Test
    void createOrUpdateThumbnail_whenCommandIsInterrupted_marksFailedAndRestoresInterrupt() throws Exception {
        ApplicationProperties properties = properties(true);
        FileCatalogItem item = catalogItem(writeSource("photo.jpg"), "jpg");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        runner.interrupt = true;
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.FAILED.name(), result.thumbnailStatus());
        assertEquals("Thumbnail generation was interrupted", result.thumbnailError());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void createOrUpdateThumbnail_whenSourceMissing_marksFailed() throws Exception {
        ApplicationProperties properties = properties(true);
        Path missingSource = tempDir.resolve("missing.mp4");
        FileCatalogItem item = catalogItem(missingSource, "mp4");
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertEquals(ThumbnailStatus.FAILED.name(), result.thumbnailStatus());
        assertEquals("Source file is not available on disk", result.thumbnailError());
        assertTrue(runner.commands.isEmpty());
    }

    @Test
    void createOrUpdateThumbnail_whenExistingThumbnailAndNotForced_skipsCommand() throws Exception {
        ApplicationProperties properties = properties(true);
        Path thumbnailPath = tempDir.resolve("thumbs").resolve("photo.jpg");
        FileCatalogItem item = catalogItemWithThumbnail(writeSource("photo.jpg"), thumbnailPath);
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, false);

        assertSame(item, result);
        assertTrue(runner.commands.isEmpty());
    }

    @Test
    void createOrUpdateThumbnail_whenExistingThumbnailAndForced_regenerates() throws Exception {
        ApplicationProperties properties = properties(true);
        Path thumbnailPath = tempDir.resolve("thumbs").resolve("photo.jpg");
        FileCatalogItem item = catalogItemWithThumbnail(writeSource("photo.jpg"), thumbnailPath);
        FakeThumbnailProcessRunner runner = new FakeThumbnailProcessRunner();
        GeneratedThumbnailService service = service(properties, runner);

        FileCatalogItem result = service.createOrUpdateThumbnail(item, true);

        assertEquals(ThumbnailStatus.CREATED.name(), result.thumbnailStatus());
        assertEquals(1, runner.commands.size());
    }

    @Test
    void deleteThumbnail_removesLocalThumbnailAtCatalogEol() throws Exception {
        ApplicationProperties properties = properties(true);
        Path thumbnailPath = tempDir.resolve("thumbs").resolve("photo.jpg");
        Files.createDirectories(thumbnailPath.getParent());
        Files.writeString(thumbnailPath, "thumbnail");
        FileCatalogItem item = catalogItemWithThumbnail(writeSource("photo.jpg"), thumbnailPath);
        GeneratedThumbnailService service = service(properties, new FakeThumbnailProcessRunner());

        service.deleteThumbnail(item);

        assertFalse(Files.exists(thumbnailPath));
    }

    @Test
    void deleteThumbnail_whenThumbnailFileIsAlreadyAbsent_doesNotFail() throws Exception {
        ApplicationProperties properties = properties(true);
        Path thumbnailPath = tempDir.resolve("thumbs").resolve("missing.jpg");
        FileCatalogItem item = catalogItemWithThumbnail(writeSource("photo.jpg"), thumbnailPath);
        GeneratedThumbnailService service = service(properties, new FakeThumbnailProcessRunner());

        service.deleteThumbnail(item);

        assertFalse(Files.exists(thumbnailPath));
    }

    private GeneratedThumbnailService service(ApplicationProperties properties, ThumbnailProcessRunner runner) {
        return new GeneratedThumbnailService(
                properties,
                new FileCatalogItemMapperImpl(),
                runner);
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

    private FileCatalogItem catalogItem(Path sourcePath, String extension) throws Exception {
        return new FileCatalogItem(
                sourcePath.toString(),
                sourcePath.getFileName().toString(),
                extension,
                sourcePath.getParent().toString(),
                false,
                Files.exists(sourcePath) ? Files.size(sourcePath) : 10L,
                new Date(),
                "crc",
                Instant.now());
    }

    private FileCatalogItem catalogItemWithThumbnail(Path sourcePath, Path thumbnailPath) throws Exception {
        FileCatalogItem item = catalogItem(sourcePath, "jpg");
        return new FileCatalogItem(
                item.absolutePath(),
                item.fileName(),
                item.fileExtension(),
                item.parentFolder(),
                item.isDirectory(),
                item.fileSize(),
                item.archiveDate(),
                item.crc32c(),
                item.lastModified(),
                thumbnailPath.toString(),
                "GENERATED",
                "image/jpeg",
                Instant.now(),
                ThumbnailStatus.CREATED.name(),
                null);
    }

    private Path writeSource(String fileName) throws Exception {
        Path sourcePath = tempDir.resolve(fileName);
        Files.writeString(sourcePath, "source");
        return sourcePath;
    }

    private static class FakeThumbnailProcessRunner implements ThumbnailProcessRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private final List<ProcessResult> commandResults = new ArrayList<>();
        private ProcessResult result = new ProcessResult(0, "");
        private boolean interrupt;

        @Override
        public ProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
            commands.add(command);
            if (interrupt) {
                throw new InterruptedException("interrupted");
            }
            ProcessResult nextResult = commandResults.isEmpty() ? result : commandResults.remove(0);
            if (nextResult.exitCode() == 0) {
                Path outputPath = Path.of(command.get(command.size() - 1));
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, "thumbnail");
            }
            return nextResult;
        }
    }
}
