package io.datapulse.etl.adapter.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * DD-9: WB finance timestamps come in two formats depending on environment
 * (sandbox returns full ISO 8601 datetime, official docs show date-only for some fields).
 * This parser tries ISO 8601 first, then falls back to date-only.
 */
public final class WbTimestampParser {

    private WbTimestampParser() {
    }

    public static OffsetDateTime parseFlexible(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.contains("T") || trimmed.contains("t")) {
            return parseIso8601(trimmed);
        }

        return parseDateOnly(trimmed);
    }

    private static OffsetDateTime parseIso8601(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return java.time.LocalDateTime.parse(value)
                        .atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException(
                        "Cannot parse WB timestamp as ISO 8601: value=%s".formatted(value), e2);
            }
        }
    }

    private static OffsetDateTime parseDateOnly(String value) {
        try {
            return LocalDate.parse(value)
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Cannot parse WB timestamp as date-only: value=%s".formatted(value), e);
        }
    }
}
