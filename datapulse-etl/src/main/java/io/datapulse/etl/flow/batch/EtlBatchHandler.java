package io.datapulse.etl.flow.batch;

import io.datapulse.domain.MarketplaceType;
import java.util.List;

public interface EtlBatchHandler<T> {

  Class<T> elementType();

  void handleBatch(
      List<T> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  );
}
