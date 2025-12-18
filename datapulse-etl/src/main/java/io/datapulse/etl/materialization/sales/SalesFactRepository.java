package io.datapulse.etl.materialization.sales;

public interface SalesFactRepository {

  void upsertWildberries(long accountId, String requestId);

  void upsertOzonPostingsFbs(long accountId, String requestId);
}
