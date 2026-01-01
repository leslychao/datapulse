package io.datapulse.etl.repository;

public interface DimWarehouseRepository {

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);

  void upsertOzonFromPostings(Long accountId, String requestId);
}
