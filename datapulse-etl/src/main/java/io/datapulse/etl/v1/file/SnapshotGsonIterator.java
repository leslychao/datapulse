package io.datapulse.etl.v1.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.integration.util.CloseableIterator;

public final class SnapshotGsonIterator<R> implements CloseableIterator<R> {

  private static final Gson GSON = new GsonBuilder().create();

  private final Class<R> elementType;
  private final JsonReader jsonReader;

  public SnapshotGsonIterator(
      JsonReader jsonReader,
      Class<R> elementType
  ) {
    this.jsonReader = Objects.requireNonNull(jsonReader, "jsonReader must not be null");
    this.elementType = Objects.requireNonNull(elementType, "elementType must not be null");
  }

  @Override
  public boolean hasNext() {
    try {
      return jsonReader.hasNext();
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Failed to read JSON snapshot for type " + elementType.getSimpleName(),
          ex
      );
    }
  }

  @Override
  public R next() {
    if (!hasNext()) {
      throw new NoSuchElementException(
          "No more elements in snapshot JSON array for type " + elementType.getSimpleName()
      );
    }
    try {
      return GSON.fromJson(jsonReader, elementType);
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "Failed to deserialize snapshot JSON element to " + elementType.getSimpleName(),
          ex
      );
    }
  }

  @Override
  public void close() {
    try {
      jsonReader.close();
    } catch (IOException ignore) {
    }
  }
}
