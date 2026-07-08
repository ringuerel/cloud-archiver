package com.homelab.ringue.cloud.archiver.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.gallery.BrowseCursor;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository.BrowseQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest(properties = {
        "spring.data.mongodb.database=gallery_browse_repository_test",
        "de.flapdoodle.mongodb.embedded.version=7.0.5"
})
@Import(GalleryBrowseRepository.class)
class GalleryBrowseRepositoryTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private GalleryBrowseRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(FileCatalogItem.class);
    }

    @Test
    void findGalleryItems_sortsByArchiveDateDescendingWithNullsLast() {
        Date newest = date(3);
        Date middle = date(2);
        Date oldest = date(1);
        insert(
                item("/media/null-a.jpg", "/media", null, false, "jpg", "CREATED"),
                item("/media/middle.jpg", "/media", middle, false, "jpg", "CREATED"),
                item("/media/null-b.jpg", "/media", null, false, "jpg", "CREATED"),
                item("/media/newest.jpg", "/media", newest, false, "jpg", "CREATED"),
                item("/media/oldest.jpg", "/media", oldest, false, "jpg", "CREATED"));

        List<FileCatalogItem> results = repository.findGalleryItems(query(null, true, null, List.of(), null, null, null, null, 10));

        assertEquals("/media/newest.jpg", results.get(0).absolutePath());
        assertEquals("/media/middle.jpg", results.get(1).absolutePath());
        assertEquals("/media/oldest.jpg", results.get(2).absolutePath());
        assertNull(results.get(3).archiveDate());
        assertNull(results.get(4).archiveDate());
    }

    @Test
    void findGalleryItems_fetchesRequestedLimitForHasMoreDetection() {
        insertNumberedItems(5);

        List<FileCatalogItem> results = repository.findGalleryItems(query(null, false, null, List.of(), null, null, null, null, 3));

        assertEquals(3, results.size());
    }

    @Test
    void findGalleryItems_usesCursorToPageThroughDataset() {
        insertNumberedItems(12);

        List<FileCatalogItem> firstPage = repository.findGalleryItems(query(null, false, null, List.of(), null, null, null, null, 5));
        FileCatalogItem lastFirstPageItem = firstPage.get(firstPage.size() - 1);
        BrowseCursor.CursorPosition cursor = new BrowseCursor.CursorPosition(
                lastFirstPageItem.archiveDate(),
                lastFirstPageItem.absolutePath());

        List<FileCatalogItem> secondPage = repository.findGalleryItems(query(null, false, null, List.of(), null, null, null, cursor, 5));

        assertEquals(5, firstPage.size());
        assertEquals(5, secondPage.size());
        assertTrue(firstPage.stream().noneMatch(first -> secondPage.contains(first)));
        assertEquals("/media/photo-05.jpg", secondPage.get(0).absolutePath());
        assertEquals("/media/photo-09.jpg", secondPage.get(4).absolutePath());
    }

    @Test
    void findGalleryItems_pathPrefixFilterExcludesItemsInOtherFolders() {
        insert(
                item("/media/trip/a.jpg", "/media/trip", date(3), false, "jpg", "CREATED"),
                item("/media/trip/nested/b.jpg", "/media/trip/nested", date(2), false, "jpg", "CREATED"),
                item("/media/other/c.jpg", "/media/other", date(1), false, "jpg", "CREATED"));

        List<FileCatalogItem> results = repository.findGalleryItems(query("/media/trip", false, null, List.of(), null, null, null, null, 10));

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(item -> item.parentFolder().startsWith("/media/trip")));
        assertFalse(results.stream().anyMatch(item -> item.parentFolder().startsWith("/media/other")));
    }

    private BrowseQuery query(
            String path,
            boolean includeDirectories,
            String fileNameContains,
            List<String> extensions,
            String thumbnailStatus,
            Date startDate,
            Date endDate,
            BrowseCursor.CursorPosition cursor,
            int limit) {
        return new BrowseQuery(path, includeDirectories, fileNameContains, extensions, thumbnailStatus,
                startDate, endDate, cursor, limit);
    }

    private void insertNumberedItems(int count) {
        List<FileCatalogItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(item(String.format("/media/photo-%02d.jpg", i), "/media", date(count - i), false, "jpg", "CREATED"));
        }
        insert(items.toArray(FileCatalogItem[]::new));
    }

    private void insert(FileCatalogItem... items) {
        mongoTemplate.insertAll(List.of(items));
    }

    private FileCatalogItem item(
            String absolutePath,
            String parentFolder,
            Date archiveDate,
            boolean directory,
            String extension,
            String thumbnailStatus) {
        String fileName = absolutePath.substring(absolutePath.lastIndexOf('/') + 1);
        return new FileCatalogItem(
                absolutePath,
                fileName,
                extension,
                parentFolder,
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

    private Date date(int day) {
        return Date.from(Instant.parse("2026-01-%02dT00:00:00Z".formatted(day)));
    }
}
