package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WbRrdIdExtractor {

  private WbRrdIdExtractor() {
  }

  public static Long extractLastRrdId(Path jsonFile) {
    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
        jsonReader.beginArray();
      } else {
        throw new IllegalStateException("WB sales file must be a JSON array: " + jsonFile);
      }

      Long lastRrdId = null;

      while (jsonReader.hasNext()) {
        jsonReader.beginObject();

        Long rrdId = null;

        while (jsonReader.hasNext()) {
          String name = jsonReader.nextName();
          if ("rrd_id".equals(name)) {
            rrdId = readNullableLong(jsonReader);
          } else {
            jsonReader.skipValue();
          }
        }

        jsonReader.endObject();

        if (rrdId != null) {
          lastRrdId = rrdId;
        }
      }

      jsonReader.endArray();
      return lastRrdId;

    } catch (IOException ex) {
      throw new IllegalStateException("Failed to extract WB rrd_id from snapshot file: " + jsonFile,
          ex);
    }
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
