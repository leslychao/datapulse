package io.datapulse.etl.repository;

public interface OrderPnlMartRepository {

  void refresh(long accountId, String requestId);
}
