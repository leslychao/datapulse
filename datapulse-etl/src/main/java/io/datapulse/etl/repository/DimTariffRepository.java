package io.datapulse.etl.repository;

public interface DimTariffRepository {

  void upsertWildberries(Long accountId, String requestId);

  void upsertOzon(Long accountId, String requestId);
}
