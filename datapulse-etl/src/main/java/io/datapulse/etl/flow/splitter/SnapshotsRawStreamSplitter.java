package io.datapulse.etl.flow.splitter;

import io.datapulse.etl.file.SnapshotIteratorFactory;
import io.datapulse.etl.file.iterator.LastMarkingIterator;
import io.datapulse.etl.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.marketplaces.dto.Snapshot;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SnapshotsRawStreamSplitter extends AbstractMessageSplitter {

  private final SnapshotIteratorFactory iteratorFactory;
  private final SnapshotJsonLayoutRegistry layoutRegistry;

  @Override
  protected Object splitMessage(Message<?> message) {
    @SuppressWarnings("unchecked")
    List<Snapshot<?>> snapshots = (List<Snapshot<?>>) message.getPayload();

    CloseableIterator<Object> concatenated = new SnapshotsConcatenatingIterator(
        snapshots == null ? List.of() : snapshots
    );

    // last=true будет только у самого последнего raw во всей последовательности
    return new LastMarkingIterator<>(concatenated);
  }

  private final class SnapshotsConcatenatingIterator implements CloseableIterator<Object> {

    private final List<Snapshot<?>> snapshots;
    private int idx = 0;
    private CloseableIterator<?> current;

    private boolean closed;

    SnapshotsConcatenatingIterator(List<Snapshot<?>> snapshots) {
      this.snapshots = snapshots;
      this.current = openNextIteratorIfAny();
    }

    @Override
    public boolean hasNext() {
      if (closed) {
        return false;
      }

      while (true) {
        if (current == null) {
          return false;
        }
        if (current.hasNext()) {
          return true;
        }
        closeCurrentQuietly();
        current = openNextIteratorIfAny();
      }
    }

    @Override
    public Object next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No more elements");
      }
      return current.next();
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      closeCurrentQuietly();
      current = null;
    }

    private CloseableIterator<?> openNextIteratorIfAny() {
      while (idx < snapshots.size()) {
        Snapshot<?> s = snapshots.get(idx++);
        if (s == null) {
          continue;
        }

        Class<?> elementType = s.elementType();
        var locator = layoutRegistry.resolve(elementType);

        return iteratorFactory.createIterator(s.file(), (Class) elementType, locator);
      }
      return null;
    }

    private void closeCurrentQuietly() {
      if (current == null) {
        return;
      }
      try {
        current.close();
      } catch (RuntimeException ignore) {
      } finally {
        current = null;
      }
    }
  }
}
