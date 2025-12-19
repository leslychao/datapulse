package io.datapulse.etl.repository;

public interface DimProductRepository {

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);
}
