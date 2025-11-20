package io.datapulse.etl.file;

import com.google.gson.stream.JsonReader;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.stereotype.Component;

@Component
public class SnapshotIteratorFactory {

  public <R> CloseableIterator<R> createIterator(
      Path file,
      Class<R> rawType,
      String snapshotId,
      JsonArrayLocator locator,
      SnapshotCommitBarrier barrier
  ) {
    if (file == null) {
      throw new IllegalArgumentException("Snapshot file must not be null");
    }
    if (rawType == null) {
      throw new IllegalArgumentException("Snapshot rawType must not be null");
    }
    if (snapshotId == null) {
      throw new IllegalArgumentException("SnapshotId must not be null");
    }
    if (locator == null) {
      throw new IllegalArgumentException("JsonArrayLocator must not be null");
    }

    JsonReader jsonReader;
    try {
      BufferedReader bufferedReader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
      jsonReader = new JsonReader(bufferedReader);
      locator.moveToArray(jsonReader);
    } catch (IOException | IllegalStateException ex) {
      throw new RuntimeException("Failed to open snapshot file as JSON array: " + file, ex);
    }

    return new SnapshotGsonIterator<>(jsonReader, rawType, snapshotId, barrier);
  }
}
