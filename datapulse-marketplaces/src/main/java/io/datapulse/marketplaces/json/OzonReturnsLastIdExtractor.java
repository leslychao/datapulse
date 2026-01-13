package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;

public final class OzonReturnsLastIdExtractor {

  private OzonReturnsLastIdExtractor() {
  }

  public static String extractLastId(Path jsonFile) {
    String raw = JsonExtractors.extractString(
        jsonFile,
        "Failed to extract OZON returns last id from snapshot file: ",
        OzonReturnsLastIdExtractor::doExtract
    );
    return JsonExtractors.normalizeNullableBlank(raw);
  }

  private static String doExtract(JsonReader reader) throws IOException {
    reader.beginObject();

    String lastId = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("returns".equals(name)) {
        lastId = readLastIdFromReturnsArray(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return lastId;
  }

  private static String readLastIdFromReturnsArray(JsonReader reader) throws IOException {
    reader.beginArray();

    String lastId = null;

    while (reader.hasNext()) {
      reader.beginObject();

      String currentId = null;

      while (reader.hasNext()) {
        String name = reader.nextName();
        if ("id".equals(name)) {
          currentId = JsonExtractors.readNullableString(reader);
        } else {
          reader.skipValue();
        }
      }

      reader.endObject();

      if (currentId != null) {
        lastId = currentId;
      }
    }

    reader.endArray();
    return lastId;
  }
}
