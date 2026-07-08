package com.homelab.ringue.cloud.archiver.service.impl;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseRequest;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.GalleryItem;
import com.homelab.ringue.cloud.archiver.domain.gallery.RestoreJobStatus;
import com.homelab.ringue.cloud.archiver.exception.InvalidDateFormatException;
import com.homelab.ringue.cloud.archiver.exception.InvalidLimitException;
import com.homelab.ringue.cloud.archiver.gallery.BrowseCursor;
import com.homelab.ringue.cloud.archiver.gallery.RestoreJobRegistry;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository.BrowseQuery;
import com.homelab.ringue.cloud.archiver.service.GalleryService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GalleryServiceImpl implements GalleryService {

    private static final int DEFAULT_LIMIT = 60;
    private static final int MAX_LIMIT = 200;

    private final GalleryBrowseRepository galleryBrowseRepository;
    private final RestoreJobRegistry restoreJobRegistry;

    public GalleryServiceImpl(GalleryBrowseRepository galleryBrowseRepository, RestoreJobRegistry restoreJobRegistry) {
        this.galleryBrowseRepository = galleryBrowseRepository;
        this.restoreJobRegistry = restoreJobRegistry;
    }

    @Override
    public BrowseResponse browse(BrowseRequest request) {
        int limit = request.limit() <= 0 ? DEFAULT_LIMIT : request.limit();
        if (limit > MAX_LIMIT) {
            throw new InvalidLimitException(limit);
        }

        BrowseCursor.CursorPosition cursorPosition = StringUtils.hasText(request.cursor())
                ? BrowseCursor.decode(request.cursor())
                : null;
        Date startDate = parseDate("startDate", request.startDate(), false);
        Date endDate = parseDate("endDate", request.endDate(), true);

        BrowseQuery query = new BrowseQuery(
                request.path(),
                request.includeDirectories(),
                request.fileNameContains(),
                parseExtensions(request.extensions()),
                request.thumbnailStatus(),
                startDate,
                endDate,
                cursorPosition,
                limit + 1);

        List<FileCatalogItem> fetchedItems = galleryBrowseRepository.findGalleryItems(query);
        boolean hasMore = fetchedItems.size() > limit;
        List<FileCatalogItem> pageItems = hasMore ? fetchedItems.subList(0, limit) : fetchedItems;
        List<GalleryItem> galleryItems = pageItems.stream()
                .map(this::toGalleryItem)
                .toList();
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? BrowseCursor.encode(pageItems.get(pageItems.size() - 1).archiveDate(),
                        pageItems.get(pageItems.size() - 1).absolutePath())
                : null;

        return new BrowseResponse(galleryItems, nextCursor, hasMore);
    }

    GalleryItem toGalleryItem(FileCatalogItem item) {
        boolean thumbnailFileExists = StringUtils.hasText(item.thumbnailPath())
                && Files.exists(Path.of(item.thumbnailPath()));
        boolean originalFileExists = Files.exists(Path.of(item.absolutePath()));
        boolean restoreInProgress = restoreJobRegistry.findJob(item.absolutePath())
                .filter(job -> job.status() == RestoreJobStatus.RUNNING)
                .isPresent();
        return toGalleryItem(item, thumbnailFileExists, originalFileExists, restoreInProgress);
    }

    GalleryItem toGalleryItem(
            FileCatalogItem item,
            boolean thumbnailFileExists,
            boolean originalFileExists,
            boolean restoreInProgress) {
        boolean thumbnailAvailable = ThumbnailStatus.CREATED.name().equals(item.thumbnailStatus()) && thumbnailFileExists;
        boolean originalAvailable = !item.isDirectory() && originalFileExists;
        boolean restoreAvailable = !originalAvailable && item.archiveDate() != null;
        String encodedPath = URLEncoder.encode(item.absolutePath(), StandardCharsets.UTF_8);

        return new GalleryItem(
                item.absolutePath(),
                item.fileName(),
                item.fileExtension(),
                item.parentFolder(),
                item.isDirectory(),
                item.fileSize(),
                item.archiveDate(),
                item.crc32c(),
                item.lastModified(),
                item.thumbnailPath(),
                item.thumbnailProvider(),
                item.thumbnailContentType(),
                item.thumbnailCreatedAt(),
                item.thumbnailStatus(),
                item.thumbnailError(),
                "/file-catalog/media/thumbnail?path=" + encodedPath,
                "/file-catalog/media/original?path=" + encodedPath,
                "/file-catalog/media/status?path=" + encodedPath,
                "/file-catalog/media/restore?path=" + encodedPath,
                thumbnailAvailable,
                originalAvailable,
                restoreAvailable,
                restoreInProgress);
    }

    private Date parseDate(String parameterName, String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(value);
            return Date.from(localDate
                    .atTime(endOfDay ? LocalTime.MAX : LocalTime.MIN)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
        } catch (RuntimeException e) {
            throw new InvalidDateFormatException(parameterName, value);
        }
    }

    private List<String> parseExtensions(String extensions) {
        if (!StringUtils.hasText(extensions)) {
            return List.of();
        }
        return List.of(extensions.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
