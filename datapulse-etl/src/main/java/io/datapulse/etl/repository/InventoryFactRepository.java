package io.datapulse.etl.repository;

public interface InventoryFactRepository {

  void upsertOzonAnalyticsStocks(long accountId, String requestId);

  void upsertOzonProductInfoStocks(long accountId, String requestId);

  void upsertWbStocks(long accountId, String requestId);
}
