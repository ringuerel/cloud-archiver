package com.homelab.ringue.cloud.archiver.repository;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.gallery.BrowseCursor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class GalleryBrowseRepository {

    private final MongoTemplate mongoTemplate;

    public GalleryBrowseRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<FileCatalogItem> findGalleryItems(BrowseQuery browseQuery) {
        Query query = new Query();
        buildCriteria(browseQuery).forEach(query::addCriteria);
        query.with(Sort.by(Sort.Order.desc("archiveDate"), Sort.Order.asc("absolutePath")));
        query.limit(browseQuery.limit());
        return mongoTemplate.find(query, FileCatalogItem.class);
    }

    private List<Criteria> buildCriteria(BrowseQuery browseQuery) {
        List<Criteria> criteria = new ArrayList<>();

        if (StringUtils.hasText(browseQuery.path())) {
            criteria.add(Criteria.where("parentFolder").regex("^" + Pattern.quote(browseQuery.path())));
        }

        if (!browseQuery.includeDirectories()) {
            criteria.add(Criteria.where("isDirectory").is(false));
        }

        if (StringUtils.hasText(browseQuery.fileNameContains())) {
            criteria.add(Criteria.where("fileName").regex(Pattern.quote(browseQuery.fileNameContains()), "i"));
        }

        if (!browseQuery.extensions().isEmpty()) {
            criteria.add(Criteria.where("fileExtension").in(browseQuery.extensions()));
        }

        if (StringUtils.hasText(browseQuery.thumbnailStatus())) {
            criteria.add(Criteria.where("thumbnailStatus").is(browseQuery.thumbnailStatus()));
        }

        Criteria archiveDateCriteria = archiveDateCriteria(browseQuery.startDate(), browseQuery.endDate());
        if (archiveDateCriteria != null) {
            criteria.add(archiveDateCriteria);
        }

        Criteria cursorCriteria = cursorCriteria(browseQuery.cursorPosition());
        if (cursorCriteria != null) {
            criteria.add(cursorCriteria);
        }

        return criteria;
    }

    private Criteria archiveDateCriteria(Date startDate, Date endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        Criteria criteria = Criteria.where("archiveDate");
        if (startDate != null) {
            criteria = criteria.gte(startDate);
        }
        if (endDate != null) {
            criteria = criteria.lte(endDate);
        }
        return criteria;
    }

    private Criteria cursorCriteria(BrowseCursor.CursorPosition cursorPosition) {
        if (cursorPosition == null) {
            return null;
        }

        Date archiveDate = cursorPosition.archiveDate();
        String absolutePath = cursorPosition.absolutePath();
        if (archiveDate == null) {
            return new Criteria().andOperator(
                    Criteria.where("archiveDate").is(null),
                    Criteria.where("absolutePath").gt(absolutePath));
        }

        return new Criteria().orOperator(
                Criteria.where("archiveDate").lt(archiveDate),
                new Criteria().andOperator(
                        Criteria.where("archiveDate").is(archiveDate),
                        Criteria.where("absolutePath").gt(absolutePath)),
                Criteria.where("archiveDate").is(null));
    }

    public record BrowseQuery(
            String path,
            boolean includeDirectories,
            String fileNameContains,
            List<String> extensions,
            String thumbnailStatus,
            Date startDate,
            Date endDate,
            BrowseCursor.CursorPosition cursorPosition,
            int limit
    ) {
        public BrowseQuery {
            extensions = extensions == null
                    ? List.of()
                    : extensions.stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .toList();
        }
    }
}
