package io.datapulse.etl.nextgen.support;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class StreamingJsonReader {

  private final JsonFactory factory = new JsonFactory();

  public List<String> readArray(InputStream inputStream) {
    List<String> rows = new ArrayList<>();
    try (JsonParser parser = factory.createParser(inputStream)) {
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        return List.of();
      }
      while (parser.nextToken() != JsonToken.END_ARRAY) {
        String value = parser.readValueAsTree().toString();
        rows.add(value);
      }
      return rows;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to stream json", ex);
    }
  }
}
