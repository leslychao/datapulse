package io.datapulse.etl.repository;

public interface LogisticsFactRepository {

  void upsertOzon(long accountId, String requestId);

  void upsertWildberries(long accountId, String requestId);
}
