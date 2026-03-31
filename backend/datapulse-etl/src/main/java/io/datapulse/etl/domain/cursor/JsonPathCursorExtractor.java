package io.datapulse.etl.domain.cursor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * For metadata cursor endpoints (~5 endpoints: WB Catalog, Ozon Catalog list,
 * Ozon Stocks/Prices/Finance).
 * Parses temp file post-write to extract cursor from a known JSON path.
 * <p>
 * Supports dot-notation for nested paths, e.g. {@code "result.cursor"} or {@code "cursor"}.
 * Overhead: < 1 ms (streaming parse, stops after finding the field).
 */
public final class JsonPathCursorExtractor implements CursorExtractor {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final int BUFFER_SIZE = 8 * 1024;

    private final String[] pathSegments;

    public JsonPathCursorExtractor(String jsonPath) {
        this.pathSegments = jsonPath.split("\\.");
    }

    @Override
    public Optional<String> extract(Path tempFile) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(tempFile), BUFFER_SIZE);
             JsonParser parser = JSON_FACTORY.createParser(is)) {

            return findValue(parser, pathSegments, 0);
        }
    }

    private Optional<String> findValue(JsonParser parser, String[] segments, int depth) throws IOException {
        String targetField = segments[depth];
        boolean isLast = depth == segments.length - 1;

        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME
                    && targetField.equals(parser.currentName())) {

                parser.nextToken();

                if (isLast) {
                    return Optional.ofNullable(parser.getValueAsString());
                }

                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    return findValue(parser, segments, depth + 1);
                }

                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
