package io.datapulse.etl.repository;

public interface FinanceFactRepository {

  void upsertFromWildberries(long accountId, String requestId);

  void upsertFromOzon(long accountId, String requestId);
}
