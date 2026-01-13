package io.datapulse.etl.file;

import com.google.gson.stream.JsonReader;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.stereotype.Component;

@Component
public class SnapshotIteratorFactory {

  public <R> CloseableIterator<R> createIterator(
      Path file,
      Class<R> elementType,
      JsonArrayLocator locator
  ) {
    Objects.requireNonNull(file, "file must not be null");
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(locator, "locator must not be null");

    try {
      BufferedReader bufferedReader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
      JsonReader jsonReader = new JsonReader(bufferedReader);
      locator.moveToArray(jsonReader);
      return new SnapshotGsonIterator<>(jsonReader, elementType);
    } catch (IOException | IllegalStateException ex) {
      throw new RuntimeException("Failed to open snapshot file as JSON array: " + file, ex);
    }
  }
}
