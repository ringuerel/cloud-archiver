package com.homelab.ringue.cloud.archiver.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homelab.ringue.cloud.archiver.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * Utility class for encoding and decoding opaque pagination cursors.
 *
 * <p>Internal JSON structure: {@code {"ad":<epochMs>,"ap":"<absolutePath>"}}
 * where {@code ad = -1} represents a null {@code archiveDate} (sorts last).
 *
 * <p>Encoding uses URL-safe Base64 without padding.
 * Decoding throws {@link InvalidCursorException} on any failure.
 */
public final class BrowseCursor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long NULL_DATE_SENTINEL = -1L;

    private BrowseCursor() {
        // utility class — no instances
    }

    /**
     * Encodes an archiveDate and absolutePath into an opaque cursor string.
     *
     * @param archiveDate  the archive date of the last item on the page, or {@code null}
     * @param absolutePath the absolute path of the last item on the page
     * @return a URL-safe base64-encoded cursor string without padding
     */
    public static String encode(Date archiveDate, String absolutePath) {
        long ad = (archiveDate == null) ? NULL_DATE_SENTINEL : archiveDate.getTime();
        String json = "{\"ad\":" + ad + ",\"ap\":\"" + escapeJson(absolutePath) + "\"}";
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque cursor string into a {@link CursorPosition}.
     *
     * @param opaqueCursor the base64-encoded cursor string
     * @return the decoded {@link CursorPosition}
     * @throws InvalidCursorException if the cursor is null, empty, or cannot be decoded/parsed
     */
    public static CursorPosition decode(String opaqueCursor) {
        try {
            byte[] jsonBytes = Base64.getUrlDecoder().decode(opaqueCursor);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            JsonNode node = OBJECT_MAPPER.readTree(json);

            long ad = node.get("ad").longValue();
            String ap = node.get("ap").asText();

            Date archiveDate = (ad == NULL_DATE_SENTINEL) ? null : new Date(ad);
            return new CursorPosition(archiveDate, ap);
        } catch (Exception e) {
            throw new InvalidCursorException(opaqueCursor);
        }
    }

    /**
     * Escapes a string value for safe embedding inside a JSON string literal.
     * Handles the characters that must be escaped per the JSON specification.
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Represents the decoded position within the sorted result set.
     *
     * @param archiveDate  the archive date of the last item on the previous page, or {@code null}
     * @param absolutePath the absolute path of the last item on the previous page
     */
    public record CursorPosition(Date archiveDate, String absolutePath) {}
}
