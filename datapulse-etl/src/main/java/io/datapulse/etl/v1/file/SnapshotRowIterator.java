package io.datapulse.etl.v1.file;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.SnapshotIngestContext;
import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.SnapshotRow;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.integration.util.CloseableIterator;


public final class SnapshotRowIterator implements CloseableIterator<SnapshotRow<?>> {

  private final CloseableIterator<?> delegate;
  private final SnapshotIngestContext ctx;

  private boolean buffered;
  private Object bufferedValue;

  private boolean emptyLastEmitted;

  public SnapshotRowIterator(CloseableIterator<?> delegate, SnapshotIngestContext ctx) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
  }

  @Override
  public boolean hasNext() {
    if (emptyLastEmitted) {
      return false;
    }
    if (buffered) {
      return true;
    }
    if (delegate.hasNext()) {
      bufferedValue = delegate.next();
      buffered = true;
      return true;
    }
    return true;
  }

  @Override
  public SnapshotRow<?> next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No more snapshot rows for " + ctx.snapshotFile());
    }

    if (!buffered && !delegate.hasNext()) {
      emptyLastEmitted = true;
      return new SnapshotRow<>(ctx, null, true);
    }

    Object row = buffered ? bufferedValue : delegate.next();
    buffered = false;
    bufferedValue = null;

    boolean last = !delegate.hasNext();
    return new SnapshotRow<>(ctx, row, last);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
