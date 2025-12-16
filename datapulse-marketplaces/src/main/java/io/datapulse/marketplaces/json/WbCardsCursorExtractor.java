package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;

public final class WbCardsCursorExtractor {

  private WbCardsCursorExtractor() {
  }

  public static String extractCursor(Path jsonFile) {
    return JsonExtractors.extractString(
        jsonFile,
        "Failed to extract WB cards cursor from snapshot file: ",
        WbCardsCursorExtractor::doExtract
    );
  }

  private static String doExtract(JsonReader reader) throws IOException {
    reader.beginObject();

    String updatedAt = null;
    Long nmId = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("cursor".equals(name)) {
        Cursor cursor = readCursor(reader);
        updatedAt = cursor.updatedAt;
        nmId = cursor.nmId;
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();

    if (updatedAt == null || nmId == null) {
      return null;
    }

    return updatedAt + "|" + nmId;
  }

  private static Cursor readCursor(JsonReader reader) throws IOException {
    reader.beginObject();

    String updatedAt = null;
    Long nmId = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("updatedAt".equals(name)) {
        updatedAt = JsonExtractors.readNullableString(reader);
      } else if ("nmID".equals(name)) {
        nmId = JsonExtractors.readNullableLong(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return new Cursor(updatedAt, nmId);
  }

  private record Cursor(String updatedAt, Long nmId) {

  }
}
