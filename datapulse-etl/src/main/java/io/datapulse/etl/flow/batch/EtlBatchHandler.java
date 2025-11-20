package io.datapulse.etl.flow.batch;

import io.datapulse.domain.MarketplaceType;
import java.util.List;

public interface EtlBatchHandler<T> {

  String rawTableName();

  Class<T> elementType();

  void handleBatch(
      List<T> rawBatch,
      Long accountId,
      MarketplaceType marketplace
  );
}
