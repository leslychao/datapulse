package io.datapulse.etl.repository;

public interface CommissionFactRepository {

  void upsertOzon(long accountId, String requestId);

  void upsertWildberries(long accountId, String requestId);
}
