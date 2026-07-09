package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseRequest;
import com.homelab.ringue.cloud.archiver.domain.gallery.BrowseResponse;

public interface GalleryService {
    BrowseResponse browse(BrowseRequest request);
}
