package io.datapulse.etl.v1.file;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.SnapshotIngestContext;
import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.SnapshotRow;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.util.CloseableIterator;

@Slf4j
public final class SnapshotRowIterator implements CloseableIterator<SnapshotRow<?>> {

  private final CloseableIterator<?> delegate;
  private final SnapshotIngestContext context;

  private Object nextRow;
  private boolean hasNextLoaded;

  public SnapshotRowIterator(
      CloseableIterator<?> delegate,
      SnapshotIngestContext context
  ) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.context = Objects.requireNonNull(context, "context must not be null");
    advance();
    if (!hasNextLoaded) {
      close();
    }
  }

  private void advance() {
    if (delegate.hasNext()) {
      nextRow = delegate.next();
      hasNextLoaded = true;
    } else {
      nextRow = null;
      hasNextLoaded = false;
    }
  }

  @Override
  public boolean hasNext() {
    return hasNextLoaded;
  }

  @Override
  public SnapshotRow<?> next() {
    Object current = nextRow;
    advance();
    boolean last = !hasNextLoaded;
    return new SnapshotRow<>(context, current, last);
  }

  @Override
  public void close() {
    try {
      delegate.close();
    } finally {
      log.debug("Snapshot file released: {}", context.snapshotFile());
    }
  }
}
