package io.datapulse.etl.repository;

public interface PenaltiesFactRepository {

  void upsertOzon(long accountId, String requestId);

  void upsertWildberries(long accountId, String requestId);
}
