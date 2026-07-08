package com.homelab.ringue.cloud.archiver.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.homelab.ringue.cloud.archiver.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class BrowseCursorTest {

    @Test
    void encodeDecode_withArchiveDate_roundTripsPosition() {
        Date archiveDate = new Date(1_720_000_000_000L);
        String absolutePath = "/media/photos/img 001.jpg";

        String cursor = BrowseCursor.encode(archiveDate, absolutePath);
        BrowseCursor.CursorPosition position = BrowseCursor.decode(cursor);

        assertEquals(archiveDate, position.archiveDate());
        assertEquals(absolutePath, position.absolutePath());
    }

    @Test
    void encodeDecode_withNullArchiveDate_roundTripsSentinelAsNull() {
        String absolutePath = "/media/photos/no-archive-date.jpg";

        String cursor = BrowseCursor.encode(null, absolutePath);
        BrowseCursor.CursorPosition position = BrowseCursor.decode(cursor);

        assertNull(position.archiveDate());
        assertEquals(absolutePath, position.absolutePath());
    }

    @Test
    void decode_withNonBase64String_throwsInvalidCursorException() {
        assertThrows(InvalidCursorException.class, () -> BrowseCursor.decode("not valid base64!"));
    }

    @Test
    void decode_withBase64InvalidJson_throwsInvalidCursorException() {
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("not-json".getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidCursorException.class, () -> BrowseCursor.decode(cursor));
    }
}
