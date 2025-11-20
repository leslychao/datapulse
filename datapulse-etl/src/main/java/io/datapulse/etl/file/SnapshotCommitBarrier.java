package io.datapulse.etl.file;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import java.nio.file.Path;

public interface SnapshotCommitBarrier {

  record SnapshotCompletionEvent(
      String snapshotId,
      Path file,
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      MarketplaceType marketplace,
      String sourceId
  ) {

  }

  @FunctionalInterface
  interface SnapshotCompletionListener {

    void onSnapshotCompleted(SnapshotCompletionEvent event);
  }

  String registerSnapshot(
      Path file,
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      MarketplaceType marketplace,
      String sourceId
  );

  void registerFirstElement(String snapshotId);

  void registerBatch(String snapshotId);

  void batchCompleted(String snapshotId);

  void snapshotCompleted(String snapshotId);

  void discard(String snapshotId, Path providedFile);

  void registerListener(SnapshotCompletionListener listener);
}
