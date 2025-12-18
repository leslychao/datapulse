package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.nio.file.Path;

public final class WbLastChangeDateExtractor {

  private WbLastChangeDateExtractor() {
  }

  public static String extractLastChangeDate(Path jsonArrayFile) {
    if (jsonArrayFile == null) {
      return null;
    }

    return JsonExtractors.extractString(
        jsonArrayFile,
        "Failed to extract lastChangeDate from WB supplier/sales snapshot file: ",
        WbLastChangeDateExtractor::doExtractLastChangeDate
    );
  }

  private static String doExtractLastChangeDate(JsonReader reader) throws IOException {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
      throw new IllegalArgumentException("WB supplier/sales response must be a JSON array.");
    }

    reader.beginArray();

    String lastChangeDate = null;

    while (reader.hasNext()) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        reader.skipValue();
        continue;
      }

      reader.beginObject();
      while (reader.hasNext()) {
        String fieldName = reader.nextName();
        if ("lastChangeDate".equals(fieldName)) {
          String value = JsonExtractors.normalizeNullableBlank(
              JsonExtractors.readNullableString(reader));
          if (value != null) {
            lastChangeDate = value;
          }
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    reader.endArray();
    return lastChangeDate;
  }
}
