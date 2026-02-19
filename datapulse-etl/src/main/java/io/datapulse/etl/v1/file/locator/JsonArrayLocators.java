package io.datapulse.etl.v1.file.locator;

import java.util.Arrays;

public final class JsonArrayLocators {

  private static final String[] EMPTY_PATH = new String[0];

  private JsonArrayLocators() {
  }

  public static JsonArrayLocator arrayAtPath(String path) {
    return arrayAtPath(normalize(path));
  }

  public static JsonArrayLocator arrayAtPath(String... pathSegments) {
    final String[] path = normalize(pathSegments);
    final String pathDescription = describePath(path);

    return reader -> {
      try {
        if (path.length == 0) {
          reader.beginArray();
          return;
        }

        reader.beginObject();

        for (int i = 0; i < path.length; i++) {
          final String current = path[i];
          boolean found = false;

          while (reader.hasNext()) {
            final String name = reader.nextName();
            if (!current.equals(name)) {
              reader.skipValue();
              continue;
            }

            if (i == path.length - 1) {
              reader.beginArray();
              return;
            }

            reader.beginObject();
            found = true;
            break;
          }

          if (!found) {
            throw missingFieldException(current, pathDescription);
          }
        }

        throw unexpectedEndOfPathException(pathDescription);
      } catch (Exception ex) {
        throw navigationFailedException(pathDescription, ex);
      }
    };
  }

  private static String[] normalize(String... pathSegments) {
    if (pathSegments == null || pathSegments.length == 0) {
      return EMPTY_PATH;
    }

    String[] normalized = Arrays.stream(pathSegments)
        .filter(segment -> segment != null && !segment.isBlank())
        .map(String::trim)
        .toArray(String[]::new);

    return normalized.length == 0 ? EMPTY_PATH : normalized;
  }

  private static String[] normalize(String path) {
    if (path == null || path.isBlank()) {
      return EMPTY_PATH;
    }
    return normalize(path.split("\\."));
  }

  private static String describePath(String[] path) {
    if (path == null || path.length == 0) {
      return "<root-array>";
    }
    return String.join(".", path);
  }

  private static IllegalStateException missingFieldException(
      String field, String pathDescription
  ) {
    return new IllegalStateException(
        String.format("Field '%s' not found while navigating '%s'", field, pathDescription)
    );
  }

  private static IllegalStateException unexpectedEndOfPathException(String pathDescription) {
    return new IllegalStateException(
        String.format("Unexpected end of path '%s'", pathDescription)
    );
  }

  private static IllegalStateException navigationFailedException(
      String pathDescription,
      Exception cause
  ) {
    return new IllegalStateException(
        String.format("Failed to position JSON reader on path '%s'[]", pathDescription),
        cause
    );
  }
}
