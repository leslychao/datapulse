package io.datapulse.etl.materialization.dim.warehouse;

public interface DimWarehouseRepository {

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);
}
