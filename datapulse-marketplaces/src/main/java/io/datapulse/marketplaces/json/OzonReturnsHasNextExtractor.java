package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;

public final class OzonReturnsHasNextExtractor {

  private OzonReturnsHasNextExtractor() {
  }

  public static boolean extractHasNext(Path jsonFile) {
    Boolean value = JsonExtractors.extractBoolean(
        jsonFile,
        "Failed to extract OZON returns has_next from snapshot file: ",
        OzonReturnsHasNextExtractor::doExtract
    );
    return Boolean.TRUE.equals(value);
  }

  private static Boolean doExtract(JsonReader reader) throws IOException {
    reader.beginObject();

    Boolean hasNext = Boolean.FALSE;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("has_next".equals(name)) {
        hasNext = JsonExtractors.readNullableBoolean(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return hasNext;
  }
}
