package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.ThumbnailStatus;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseRequest;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseResponse;
import com.homelab.ringue.cloud.archiver.domain.gallery.GalleryItem;
import com.homelab.ringue.cloud.archiver.exception.InvalidCursorException;
import com.homelab.ringue.cloud.archiver.exception.InvalidDateFormatException;
import com.homelab.ringue.cloud.archiver.exception.InvalidLimitException;
import com.homelab.ringue.cloud.archiver.gallery.RestoreJobRegistry;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class GalleryServiceImplPropertyTest {

    @Property
    void browseAlwaysReturnsValidStructure(@ForAll("validBrowseRequests") BrowseRequest request) {
        GalleryServiceImpl service = serviceReturning(List.of());

        BrowseResponse response = service.browse(request);

        assertNotNull(response.items());
        if (response.hasMore()) {
            assertNotNull(response.nextCursor());
        } else {
            assertNull(response.nextCursor());
        }
    }

    @Property
    void browseResultsPreserveRepositorySortOrder(@ForAll("catalogItems") List<FileCatalogItem> items) {
        List<FileCatalogItem> sortedItems = items.stream()
                .sorted(repositorySortOrder())
                .toList();
        GalleryServiceImpl service = serviceReturning(sortedItems);

        List<GalleryItem> results = service.browse(validRequest(200)).items();

        for (int i = 0; i < results.size() - 1; i++) {
            Date current = results.get(i).archiveDate();
            Date next = results.get(i + 1).archiveDate();
            if (current == null) {
                assertNull(next);
            } else if (next != null) {
                assertFalse(current.before(next));
            }
        }
    }

    @Property
    void limitAbove200ThrowsInvalidLimit(@ForAll @IntRange(min = 201, max = 1000) int limit) {
        GalleryServiceImpl service = serviceReturning(List.of());

        assertThrows(InvalidLimitException.class, () -> service.browse(validRequest(limit)));
    }

    @Property
    void invalidCursorThrowsInvalidCursor(@ForAll("invalidCursorStrings") String cursor) {
        GalleryServiceImpl service = serviceReturning(List.of());
        BrowseRequest request = new BrowseRequest(null, cursor, 60, null, null, null, null, null, false);

        assertThrows(InvalidCursorException.class, () -> service.browse(request));
    }

    @Property
    void invalidStartDateThrowsInvalidDateFormat(@ForAll("nonIsoDateStrings") String date) {
        GalleryServiceImpl service = serviceReturning(List.of());
        BrowseRequest request = new BrowseRequest(null, null, 60, null, null, date, null, null, false);

        assertThrows(InvalidDateFormatException.class, () -> service.browse(request));
    }

    @Property
    void invalidEndDateThrowsInvalidDateFormat(@ForAll("nonIsoDateStrings") String date) {
        GalleryServiceImpl service = serviceReturning(List.of());
        BrowseRequest request = new BrowseRequest(null, null, 60, null, null, null, date, null, false);

        assertThrows(InvalidDateFormatException.class, () -> service.browse(request));
    }

    @Property
    void availabilityFlagsMatchCatalogAndFilesystemState(
            @ForAll("thumbnailStatuses") String thumbnailStatus,
            @ForAll boolean archiveDatePresent,
            @ForAll boolean directory,
            @ForAll boolean thumbnailFileExists,
            @ForAll boolean originalFileExists,
            @ForAll boolean restoreInProgress) {
        GalleryServiceImpl service = serviceReturning(List.of());
        FileCatalogItem item = item("/media/photo.jpg", archiveDatePresent ? new Date() : null, directory, thumbnailStatus);

        GalleryItem galleryItem = service.toGalleryItem(item, thumbnailFileExists, originalFileExists, restoreInProgress);

        boolean expectedThumbnailAvailable = ThumbnailStatus.CREATED.name().equals(thumbnailStatus) && thumbnailFileExists;
        boolean expectedOriginalAvailable = !directory && originalFileExists;
        boolean expectedRestoreAvailable = !expectedOriginalAvailable && archiveDatePresent;
        assertEquals(expectedThumbnailAvailable, galleryItem.thumbnailAvailable());
        assertEquals(expectedOriginalAvailable, galleryItem.originalAvailable());
        assertEquals(expectedRestoreAvailable, galleryItem.restoreAvailable());
        assertEquals(restoreInProgress, galleryItem.restoreInProgress());
    }

    @Property
    void mediaUrlsContainEncodedAbsolutePath(@ForAll("absolutePaths") String absolutePath) {
        GalleryServiceImpl service = serviceReturning(List.of());
        FileCatalogItem item = item(absolutePath, new Date(), false, ThumbnailStatus.CREATED.name());

        GalleryItem galleryItem = service.toGalleryItem(item, false, false, false);

        String encoded = "path=" + URLEncoder.encode(absolutePath, StandardCharsets.UTF_8);
        assertTrue(galleryItem.thumbnailUrl().contains(encoded));
        assertTrue(galleryItem.originalUrl().contains(encoded));
        assertTrue(galleryItem.statusUrl().contains(encoded));
        assertTrue(galleryItem.restoreUrl().contains(encoded));
    }

    @Provide
    Arbitrary<BrowseRequest> validBrowseRequests() {
        return Arbitraries.integers().between(1, 200)
                .map(this::validRequest);
    }

    @Provide
    Arbitrary<String> invalidCursorStrings() {
        return Arbitraries.of("not valid base64!", "abc", "bm90LWpzb24", "eyJhZCI6Im5hbiJ9");
    }

    @Provide
    Arbitrary<String> nonIsoDateStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> thumbnailStatuses() {
        return Arbitraries.of(
                ThumbnailStatus.CREATED.name(),
                ThumbnailStatus.SKIPPED.name(),
                ThumbnailStatus.FAILED.name(),
                null);
    }

    @Provide
    Arbitrary<String> absolutePaths() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 /_-.&é")
                .ofMinLength(1)
                .ofMaxLength(80);
    }

    @Provide
    Arbitrary<List<FileCatalogItem>> catalogItems() {
        return Arbitraries.integers().between(1, 30)
                .list()
                .ofMinSize(1)
                .ofMaxSize(50)
                .map(values -> values.stream()
                        .map(value -> item(
                                "/media/photo-" + Math.abs(value) + ".jpg",
                                value % 5 == 0 ? null : new Date(Math.abs(value.longValue()) * 1_000L),
                                false,
                                ThumbnailStatus.CREATED.name()))
                        .toList());
    }

    private GalleryServiceImpl serviceReturning(List<FileCatalogItem> items) {
        GalleryBrowseRepository repository = mock(GalleryBrowseRepository.class);
        when(repository.findGalleryItems(any())).thenReturn(items);
        return new GalleryServiceImpl(repository, new RestoreJobRegistry());
    }

    private BrowseRequest validRequest(int limit) {
        return new BrowseRequest(null, null, limit, null, null, "2026-01-01", "2026-12-31", null, false);
    }

    private FileCatalogItem item(String absolutePath, Date archiveDate, boolean directory, String thumbnailStatus) {
        String fileName = absolutePath.substring(Math.max(0, absolutePath.lastIndexOf('/') + 1));
        return new FileCatalogItem(
                absolutePath,
                fileName,
                "jpg",
                "/media",
                directory,
                100L,
                archiveDate,
                "crc",
                Instant.now(),
                absolutePath + ".thumb",
                "GENERATED",
                "image/jpeg",
                Instant.now(),
                thumbnailStatus,
                null);
    }

    private Comparator<FileCatalogItem> repositorySortOrder() {
        return Comparator
                .comparing(FileCatalogItem::archiveDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FileCatalogItem::absolutePath);
    }
}
