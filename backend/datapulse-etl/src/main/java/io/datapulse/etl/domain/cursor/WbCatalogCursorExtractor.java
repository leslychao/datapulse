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
 * Extracts WB Catalog composite cursor: {@code cursor.updatedAt}, {@code cursor.nmID},
 * {@code cursor.total} from the response body.
 * <p>
 * Returns a pipe-separated string {@code "updatedAt|nmID|total"} so that the adapter
 * can parse both fields for the next request and use {@code total} for termination.
 * <p>
 * Per WB API contract: termination when {@code cursor.total < limit}.
 */
public final class WbCatalogCursorExtractor implements CursorExtractor {

  public static final WbCatalogCursorExtractor INSTANCE = new WbCatalogCursorExtractor();

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final int BUFFER_SIZE = 8 * 1024;

  private WbCatalogCursorExtractor() {}

  @Override
  public Optional<String> extract(Path tempFile) throws IOException {
    try (InputStream is = new BufferedInputStream(
        Files.newInputStream(tempFile), BUFFER_SIZE);
         JsonParser parser = JSON_FACTORY.createParser(is)) {
      return findCursorObject(parser);
    }
  }

  private Optional<String> findCursorObject(JsonParser parser) throws IOException {
    while (parser.nextToken() != null) {
      if (parser.currentToken() == JsonToken.FIELD_NAME
          && "cursor".equals(parser.currentName())) {
        parser.nextToken();
        if (parser.currentToken() == JsonToken.START_OBJECT) {
          return parseCursorFields(parser);
        }
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<String> parseCursorFields(JsonParser parser) throws IOException {
    String updatedAt = "";
    long nmId = 0;
    int total = 0;

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      if (parser.currentToken() == JsonToken.FIELD_NAME) {
        String fieldName = parser.currentName();
        parser.nextToken();
        switch (fieldName) {
          case "updatedAt" -> updatedAt = parser.getValueAsString("");
          case "nmID" -> nmId = parser.getValueAsLong(0);
          case "total" -> total = parser.getValueAsInt(0);
          default -> { }
        }
      }
    }

    return Optional.of(updatedAt + "|" + nmId + "|" + total);
  }

  /**
   * Parses the composite cursor string back into its components.
   */
  public static WbCatalogCursor parse(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return new WbCatalogCursor("", 0, 0);
    }
    String[] parts = cursor.split("\\|", 3);
    if (parts.length < 3) {
      return new WbCatalogCursor("", 0, 0);
    }
    return new WbCatalogCursor(
        parts[0],
        Long.parseLong(parts[1]),
        Integer.parseInt(parts[2]));
  }

  public record WbCatalogCursor(String updatedAt, long nmId, int total) {}
}
