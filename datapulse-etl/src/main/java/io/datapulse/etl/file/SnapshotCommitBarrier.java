package io.datapulse.etl.file;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import java.nio.file.Path;

public interface SnapshotCommitBarrier {

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

  void discard(String snapshotId);
}
