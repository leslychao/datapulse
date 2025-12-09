package io.datapulse.etl.file;

import io.datapulse.etl.flow.core.EtlSnapshotIngestionFlowConfig.IngestContext;
import io.datapulse.etl.flow.core.EtlSnapshotIngestionFlowConfig.IngestItem;
import java.util.Objects;
import org.springframework.integration.util.CloseableIterator;

public final class IngestItemIterator implements CloseableIterator<IngestItem<?>> {

  private final CloseableIterator<?> delegate;
  private final IngestContext context;

  private Object nextRow;
  private boolean hasNextLoaded;

  public IngestItemIterator(
      CloseableIterator<?> delegate,
      IngestContext context
  ) {
    this.delegate = Objects.requireNonNull(delegate);
    this.context = Objects.requireNonNull(context);
    advance();
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
    delegate.close();
  }
}
