package io.datapulse.etl.file.iterator;

import io.datapulse.etl.dto.StreamItem;
import java.util.NoSuchElementException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.util.CloseableIterator;

@Slf4j
public final class LastMarkingIterator<T> implements CloseableIterator<StreamItem<T>> {

  private final CloseableIterator<T> delegate;

  private T next;
  private boolean hasNextLoaded;
  private boolean closed;

  public LastMarkingIterator(CloseableIterator<T> delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    prefetch();
  }

  private void prefetch() {
    if (closed) {
      next = null;
      hasNextLoaded = false;
      return;
    }

    if (delegate.hasNext()) {
      next = delegate.next();
      hasNextLoaded = true;
    } else {
      next = null;
      hasNextLoaded = false;
    }
  }

  @Override
  public boolean hasNext() {
    return hasNextLoaded;
  }

  @Override
  public StreamItem<T> next() {
    if (!hasNextLoaded) {
      throw new NoSuchElementException("No more elements");
    }

    T current = next;
    prefetch();
    boolean last = !hasNextLoaded;

    return StreamItem.of(current, last);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      delegate.close();
    } catch (RuntimeException ex) {
      log.warn("Failed to close delegate iterator", ex);
      throw ex;
    }
  }
}
