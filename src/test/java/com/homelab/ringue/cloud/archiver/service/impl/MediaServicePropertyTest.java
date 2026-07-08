package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.homelab.ringue.cloud.archiver.service.MediaService;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

class MediaServicePropertyTest {

    @Property
    void etagMatchesSpecifiedFormat(
            @ForAll @LongRange(min = 0) long size,
            @ForAll @LongRange(min = 0) long lastModifiedMs) {
        String etag = MediaService.computeETag(size, lastModifiedMs);

        assertEquals("\"" + size + "-" + lastModifiedMs + "\"", etag);
    }
}
