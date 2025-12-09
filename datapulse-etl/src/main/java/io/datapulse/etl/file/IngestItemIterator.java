package io.datapulse.etl.file;

import io.datapulse.etl.flow.core.EtlSnapshotIngestionFlowConfig.IngestContext;
import io.datapulse.etl.flow.core.EtlSnapshotIngestionFlowConfig.IngestItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
      deleteSnapshot();
    }
  }

  private void deleteSnapshot() {
    Path file = context.snapshotFile();
    if (file == null) {
      log.warn("Snapshot file delete skipped: null file");
      return;
    }
    try {
      boolean deleted = Files.deleteIfExists(file);
      log.info("Snapshot file delete: file={}, deleted={}", file, deleted);
    } catch (IOException ex) {
      log.warn(
          "Snapshot file delete failed: file={}, rootCause={}",
          file,
          ExceptionUtils.getRootCause(ex)
      );
    }
  }
}
