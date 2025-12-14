package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SnapshotCursorExtractor {

  private SnapshotCursorExtractor() {
  }

  public static String extractCursor(Path jsonFile) {
    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      jsonReader.beginObject();

      String cursor = null;

      while (jsonReader.hasNext()) {
        String name = jsonReader.nextName();
        if ("cursor".equals(name)) {
          cursor = readNullableString(jsonReader);
        } else {
          jsonReader.skipValue();
        }
      }

      jsonReader.endObject();
      return normalize(cursor);

    } catch (IOException ex) {
      throw new IllegalStateException("Failed to extract cursor from snapshot file: " + jsonFile, ex);
    }
  }

  private static String readNullableString(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    return reader.nextString();
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
