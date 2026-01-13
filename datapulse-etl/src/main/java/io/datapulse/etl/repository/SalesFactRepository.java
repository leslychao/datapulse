package io.datapulse.etl.repository;

public interface SalesFactRepository {

  void upsertWildberries(long accountId, String requestId);

  void upsertOzonPostingsFbs(long accountId, String requestId);

  void upsertOzonPostingsFbo(long accountId, String requestId);
}
