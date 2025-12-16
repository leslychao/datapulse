package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;

public final class OzonCursorExtractor {

  private OzonCursorExtractor() {
  }

  public static String extractCursor(Path jsonFile) {
    String raw = JsonExtractors.extractString(
        jsonFile,
        "Failed to extract OZON cursor from snapshot file: ",
        OzonCursorExtractor::doExtract
    );
    return JsonExtractors.normalizeNullableBlank(raw);
  }

  private static String doExtract(JsonReader reader) throws IOException {
    reader.beginObject();

    String cursor = null;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("cursor".equals(name)) {
        cursor = JsonExtractors.readNullableString(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return cursor;
  }
}
