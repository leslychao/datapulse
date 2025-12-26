package io.datapulse.etl.repository;

public interface ReturnsFactRepository {

  void upsertWildberries(long accountId, String requestId);

  void upsertOzonReturns(long accountId, String requestId);
}
