package io.datapulse.etl.file;

import io.datapulse.etl.flow.core.EtlSnapshotIngestionFlowConfig.IngestContext;
import io.datapulse.etl.flow.core.EtlSnapshotIngestionFlowConfig.IngestItem;
import java.util.Objects;
import org.springframework.integration.util.CloseableIterator;

public final class IngestItemIterator implements CloseableIterator<IngestItem<?>> {

  private final CloseableIterator<?> delegate;
  private final IngestContext context;

  public IngestItemIterator(
      CloseableIterator<?> delegate,
      IngestContext context
  ) {
    this.delegate = Objects.requireNonNull(delegate);
    this.context = Objects.requireNonNull(context);
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext();
  }

  @Override
  public IngestItem<?> next() {
    Object row = delegate.next();
    return new IngestItem<>(context, row);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
