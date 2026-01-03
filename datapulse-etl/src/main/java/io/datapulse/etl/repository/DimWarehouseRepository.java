package io.datapulse.etl.repository;

public interface DimWarehouseRepository {

  void upsertOzonFromReturns(Long accountId, String requestId);

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);

  void upsertOzonFromPostings(Long accountId, String requestId);

  void upsertOzonFromTransactions(Long accountId, String requestId);

  void upsertWildberriesFromReportDetails(Long accountId, String requestId);
}
