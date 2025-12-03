package io.datapulse.etl.file.locator;

import java.util.Arrays;

public final class JsonArrayLocators {

  private JsonArrayLocators() {
  }

  public static JsonArrayLocator arrayAtPath(String... pathSegments) {
    String[] path = normalize(pathSegments);
    String pathDescription = describePath(path);

    return reader -> {
      try {
        if (path.length == 0) {
          reader.beginArray();
          return;
        }

        reader.beginObject();

        for (int i = 0; i < path.length; i++) {
          String current = path[i];
          boolean found = false;

          while (reader.hasNext()) {
            String name = reader.nextName();
            if (current.equals(name)) {
              if (i == path.length - 1) {
                reader.beginArray();
                return;
              }

              reader.beginObject();
              found = true;
              break;
            }

            reader.skipValue();
          }

          if (!found) {
            throw new IllegalStateException(
                String.format("Field '%s' not found while navigating '%s'", current, pathDescription));
          }
        }

        throw new IllegalStateException(String.format("Unexpected end of path '%s'", pathDescription));
      } catch (Exception ex) {
        throw new IllegalStateException(
            String.format("Failed to position JSON reader on path '%s'[]", pathDescription), ex);
      }
    };
  }

  public static JsonArrayLocator arrayAtPath(String path) {
    return arrayAtPath(normalize(path));
  }

  private static String[] normalize(String... pathSegments) {
    if (pathSegments == null || pathSegments.length == 0) {
      return new String[0];
    }

    return Arrays.stream(pathSegments)
        .filter(segment -> segment != null && !segment.isBlank())
        .map(String::trim)
        .toArray(String[]::new);
  }

  private static String[] normalize(String path) {
    if (path == null || path.isBlank()) {
      return new String[0];
    }

    return normalize(path.split("\\."));
  }

  private static String describePath(String[] path) {
    if (path == null || path.length == 0) {
      return "<root-array>";
    }

    return String.join(".", path);
  }
}
