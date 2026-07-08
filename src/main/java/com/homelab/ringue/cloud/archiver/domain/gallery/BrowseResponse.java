package com.homelab.ringue.cloud.archiver.domain.gallery;

import java.util.List;

public record BrowseResponse(
    List<GalleryItem> items,
    String nextCursor,
    boolean hasMore
) {}
