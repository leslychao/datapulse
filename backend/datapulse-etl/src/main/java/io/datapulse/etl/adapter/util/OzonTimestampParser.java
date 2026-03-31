package io.datapulse.etl.adapter.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * DD-6: Ozon finance timestamps use custom format "yyyy-MM-dd HH:mm:ss"
 * in Moscow timezone (UTC+3), NOT ISO 8601.
 */
public final class OzonTimestampParser {

    private static final DateTimeFormatter OZON_FINANCE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset MOSCOW_OFFSET = ZoneOffset.ofHours(3);

    private OzonTimestampParser() {
    }

    public static OffsetDateTime parseFinanceTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDateTime local = LocalDateTime.parse(value.trim(), OZON_FINANCE_FMT);
            return OffsetDateTime.of(local, MOSCOW_OFFSET);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Cannot parse Ozon finance timestamp: value=%s".formatted(value), e);
        }
    }

    public static OffsetDateTime parseIso8601(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Cannot parse Ozon ISO 8601 timestamp: value=%s".formatted(value), e);
        }
    }
}
