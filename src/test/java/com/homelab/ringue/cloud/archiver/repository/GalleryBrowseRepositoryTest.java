package com.homelab.ringue.cloud.archiver.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.gallery.BrowseCursor;
import com.homelab.ringue.cloud.archiver.repository.GalleryBrowseRepository.BrowseQuery;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class GalleryBrowseRepositoryTest {

    private MongoTemplate mongoTemplate;
    private GalleryBrowseRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        repository = new GalleryBrowseRepository(mongoTemplate);
    }

    @Test
    void findGalleryItems_appliesSortLimitAndDelegatesToMongoTemplate() {
        List<FileCatalogItem> expectedItems = List.of(item("/media/photo.jpg", "/media", date(1), false, "jpg", "CREATED"));
        when(mongoTemplate.find(captureAnyQuery(), eq(FileCatalogItem.class))).thenReturn(expectedItems);

        List<FileCatalogItem> results = repository.findGalleryItems(
                query(null, false, null, List.of(), null, null, null, null, 3));

        assertSame(expectedItems, results);
        Query captured = capturedQuery();
        assertEquals(3, captured.getLimit());
        assertEquals(-1, captured.getSortObject().get("archiveDate"));
        assertEquals(1, captured.getSortObject().get("absolutePath"));
        assertEquals(false, captured.getQueryObject().get("isDirectory"));
    }

    @Test
    void findGalleryItems_buildsPathPrefixFilter() {
        when(mongoTemplate.find(captureAnyQuery(), eq(FileCatalogItem.class))).thenReturn(List.of());

        repository.findGalleryItems(query("/media/trip", false, null, List.of(), null, null, null, null, 10));

        String queryJson = capturedQuery().getQueryObject().toJson();
        assertTrue(queryJson.contains("parentFolder"));
        assertTrue(queryJson.contains("^\\\\Q/media/trip\\\\E"));
        assertTrue(queryJson.contains("isDirectory"));
    }

    @Test
    void findGalleryItems_buildsFilterCriteria() {
        Date startDate = date(1);
        Date endDate = date(3);
        when(mongoTemplate.find(captureAnyQuery(), eq(FileCatalogItem.class))).thenReturn(List.of());

        repository.findGalleryItems(query(null, true, "vacation", List.of("jpg", "png"), "CREATED",
                startDate, endDate, null, 20));

        String queryJson = capturedQuery().getQueryObject().toJson();
        assertTrue(queryJson.contains("fileName"));
        assertTrue(queryJson.contains("vacation"));
        assertTrue(queryJson.contains("fileExtension"));
        assertTrue(queryJson.contains("jpg"));
        assertTrue(queryJson.contains("png"));
        assertTrue(queryJson.contains("thumbnailStatus"));
        assertTrue(queryJson.contains("CREATED"));
        assertTrue(queryJson.contains("$gte"));
        assertTrue(queryJson.contains("$lte"));
    }

    @Test
    void findGalleryItems_buildsCursorPredicateForDatedCursor() {
        BrowseCursor.CursorPosition cursor = new BrowseCursor.CursorPosition(date(2), "/media/photo-05.jpg");
        when(mongoTemplate.find(captureAnyQuery(), eq(FileCatalogItem.class))).thenReturn(List.of());

        repository.findGalleryItems(query(null, false, null, List.of(), null, null, null, cursor, 5));

        String queryJson = capturedQuery().getQueryObject().toJson();
        assertTrue(queryJson.contains("$or"));
        assertTrue(queryJson.contains("$lt"));
        assertTrue(queryJson.contains("$gt"));
        assertTrue(queryJson.contains("/media/photo-05.jpg"));
        assertTrue(queryJson.contains("archiveDate"));
        assertTrue(queryJson.contains("absolutePath"));
    }

    @Test
    void findGalleryItems_buildsCursorPredicateForNullDateCursor() {
        BrowseCursor.CursorPosition cursor = new BrowseCursor.CursorPosition(null, "/media/photo-05.jpg");
        when(mongoTemplate.find(captureAnyQuery(), eq(FileCatalogItem.class))).thenReturn(List.of());

        repository.findGalleryItems(query(null, false, null, List.of(), null, null, null, cursor, 5));

        String queryJson = capturedQuery().getQueryObject().toJson();
        assertTrue(queryJson.contains("archiveDate"));
        assertTrue(queryJson.contains("null"));
        assertTrue(queryJson.contains("$gt"));
        assertTrue(queryJson.contains("/media/photo-05.jpg"));
    }

    private Query captureAnyQuery() {
        return org.mockito.ArgumentMatchers.any(Query.class);
    }

    private Query capturedQuery() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(FileCatalogItem.class));
        return queryCaptor.getValue();
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
