package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class OzonResultSizeExtractor {

  private OzonResultSizeExtractor() {
  }

  public static int extractResultSize(Path jsonFile) {
    return IntExtractors.extractInt(
        jsonFile,
        "Failed to extract OZON result size from snapshot file: ",
        OzonResultSizeExtractor::doExtract
    );
  }

  private static int doExtract(JsonReader reader) throws IOException {
    reader.beginObject();

    int resultSize = 0;

    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("result".equals(name)) {
        resultSize = countArray(reader);
      } else {
        reader.skipValue();
      }
    }

    reader.endObject();
    return resultSize;
  }

  private static int countArray(JsonReader reader) throws IOException {
    reader.beginArray();

    int count = 0;
    while (reader.hasNext()) {
      reader.skipValue();
      count++;
    }

    reader.endArray();
    return count;
  }

  private interface IntExtractor {

    int extract(JsonReader reader) throws IOException;
  }

  private static final class IntExtractors {

    private IntExtractors() {
    }

    static int extractInt(
        Path jsonFile,
        String failureMessagePrefix,
        IntExtractor extractor
    ) {
      try (var bufferedReader = java.nio.file.Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
          var jsonReader = new JsonReader(bufferedReader)) {

        return extractor.extract(jsonReader);

      } catch (IOException ex) {
        throw new IllegalStateException(failureMessagePrefix + jsonFile, ex);
      }
    }
  }
}
