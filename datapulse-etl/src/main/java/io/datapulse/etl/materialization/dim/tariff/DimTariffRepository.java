package io.datapulse.etl.materialization.dim.tariff;

public interface DimTariffRepository {

  void upsertWildberries(Long accountId, String requestId);
}
