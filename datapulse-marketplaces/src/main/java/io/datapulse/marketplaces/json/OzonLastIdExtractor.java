package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;

public final class OzonLastIdExtractor {

  private OzonLastIdExtractor() {
  }

  public static String extractLastId(Path jsonFile) {
    String raw = JsonExtractors.extractString(
        jsonFile,
        "Failed to extract OZON last_id from snapshot file: ",
        OzonLastIdExtractor::doExtract
    );
    return JsonExtractors.normalizeNullableBlank(raw);
  }

  private static String doExtract(JsonReader reader) throws IOException {
    reader.beginObject();

    String lastId = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("result".equals(name)) {
        lastId = readLastIdFromResult(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return lastId;
  }

  private static String readLastIdFromResult(JsonReader reader) throws IOException {
    reader.beginObject();

    String lastId = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("last_id".equals(name)) {
        lastId = JsonExtractors.readNullableString(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return lastId;
  }
}
