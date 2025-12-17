package io.datapulse.etl.materialization.sales;

public interface SalesFactRepository {

  void upsertWildberries(Long accountId, String requestId);

  void upsertOzonPostingsFbs(Long accountId, String requestId);

  void upsertOzonFinanceTransactions(Long accountId, String requestId);
}
