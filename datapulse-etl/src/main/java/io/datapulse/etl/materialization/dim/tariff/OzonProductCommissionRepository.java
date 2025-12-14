package io.datapulse.etl.materialization.dim.tariff;

public interface OzonProductCommissionRepository {

  void upsertOzon(Long accountId, String requestId);
}
