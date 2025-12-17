package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OzonHasNextExtractor {

  private OzonHasNextExtractor() {
  }

  public static boolean extractHasNext(Path jsonFile) {
    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      jsonReader.beginObject();

      while (jsonReader.hasNext()) {
        String name = jsonReader.nextName();
        if ("result".equals(name)) {
          return readHasNextFromResult(jsonReader);
        }
        jsonReader.skipValue();
      }

      jsonReader.endObject();
      return false;

    } catch (IOException ex) {
      throw new IllegalStateException(
          "Failed to extract Ozon has_next from snapshot file: " + jsonFile, ex);
    }
  }

  private static boolean readHasNextFromResult(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return false;
    }

    reader.beginObject();
    boolean hasNext = false;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("has_next".equals(name)) {
        hasNext = readNullableBoolean(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return hasNext;
  }

  private static boolean readNullableBoolean(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return false;
    }
    if (reader.peek() == JsonToken.BOOLEAN) {
      return reader.nextBoolean();
    }
    String raw = reader.nextString();
    String trimmed = raw == null ? "" : raw.trim();
    return Boolean.parseBoolean(trimmed);
  }
}
