package io.datapulse.etl.file;

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
  private final String snapshotId;
  private final SnapshotCommitBarrier snapshotCommitBarrier;

  private boolean endOfArrayReached = false;
  private boolean closed = false;
  private boolean completionSignalled = false;
  private boolean firstElementRegistered = false;

  public SnapshotGsonIterator(
      JsonReader jsonReader,
      Class<R> elementType,
      String snapshotId,
      SnapshotCommitBarrier snapshotCommitBarrier
  ) {
    this.jsonReader = Objects.requireNonNull(jsonReader, "jsonReader must not be null");
    this.elementType = Objects.requireNonNull(elementType, "elementType must not be null");
    this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId must not be null");
    this.snapshotCommitBarrier =
        Objects.requireNonNull(snapshotCommitBarrier, "snapshotCommitBarrier must not be null");
  }

  @Override
  public boolean hasNext() {
    if (closed || endOfArrayReached) {
      return false;
    }
    try {
      boolean hasNext = jsonReader.hasNext();
      if (!hasNext) {
        jsonReader.endArray();
        endOfArrayReached = true;
        signalCompletionIfNeeded();
      }
      return hasNext;
    } catch (IOException | RuntimeException ex) {
      close();
      throw new RuntimeException("Failed to read snapshot JSON: " + snapshotId, ex);
    }
  }

  @Override
  public R next() {
    if (closed) {
      throw new IllegalStateException(
          "Iterator is already closed for snapshot: " + snapshotId
      );
    }
    if (!hasNext()) {
      throw new NoSuchElementException(
          "No more elements in snapshot JSON array: " + snapshotId
      );
    }
    try {
      if (!firstElementRegistered) {
        firstElementRegistered = true;
        snapshotCommitBarrier.registerFirstElement(snapshotId);
      }
      return GSON.fromJson(jsonReader, elementType);
    } catch (RuntimeException ex) {
      close();
      throw new RuntimeException(
          "Failed to deserialize snapshot JSON element: " + snapshotId,
          ex
      );
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      jsonReader.close();
    } catch (IOException ignore) {
    } finally {
      signalCompletionIfNeeded();
    }
  }

  private void signalCompletionIfNeeded() {
    if (completionSignalled) {
      return;
    }
    completionSignalled = true;
    snapshotCommitBarrier.snapshotCompleted(snapshotId);
  }
}
