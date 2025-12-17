package io.datapulse.etl.materialization.dim.product;

public interface DimProductRepository {

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);
}
