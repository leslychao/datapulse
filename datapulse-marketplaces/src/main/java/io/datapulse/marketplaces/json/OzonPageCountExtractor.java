package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OzonPageCountExtractor {

  private OzonPageCountExtractor() {
  }

  public static long extractPageCount(Path jsonFile) {
    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      jsonReader.beginObject();

      while (jsonReader.hasNext()) {
        String name = jsonReader.nextName();
        if ("result".equals(name)) {
          return readPageCountFromResult(jsonReader);
        }
        jsonReader.skipValue();
      }

      jsonReader.endObject();
      return 0L;

    } catch (IOException ex) {
      throw new IllegalStateException("Failed to extract Ozon page_count from snapshot file: " + jsonFile, ex);
    }
  }

  private static long readPageCountFromResult(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return 0L;
    }

    reader.beginObject();

    Long pageCount = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("page_count".equals(name)) {
        pageCount = readNullableLong(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return pageCount == null ? 0L : pageCount;
  }

  private static Long readNullableLong(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    if (reader.peek() == JsonToken.NUMBER) {
      return reader.nextLong();
    }
    String raw = reader.nextString();
    String trimmed = raw == null ? "" : raw.trim();
    return trimmed.isEmpty() ? null : Long.parseLong(trimmed);
  }
}
