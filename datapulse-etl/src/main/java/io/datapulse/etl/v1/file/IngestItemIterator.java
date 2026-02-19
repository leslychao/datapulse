package io.datapulse.etl.v1.file;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestContext;
import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestItem;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.util.CloseableIterator;

@Slf4j
public final class IngestItemIterator implements CloseableIterator<IngestItem<?>> {

  private final CloseableIterator<?> delegate;
  private final IngestContext context;

  private Object nextRow;
  private boolean hasNextLoaded;

  public IngestItemIterator(
      CloseableIterator<?> delegate,
      IngestContext context
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
  public IngestItem<?> next() {
    Object current = nextRow;
    advance();
    boolean last = !hasNextLoaded;
    return new IngestItem<>(context, current, last);
  }

  @Override
  public void close() {
    try {
      delegate.close();
    } finally {
      log.debug("Snapshot file retained: {}", context.snapshotFile());
    }
  }
}
