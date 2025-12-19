package io.datapulse.etl.repository;

public interface OzonProductCommissionRepository {

  void upsertOzon(Long accountId, String requestId);
}
