package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class WarehouseMaterializationHandler implements MaterializationHandler {

  private final DimWarehouseRepository repository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.WAREHOUSE_DICT;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Warehouse materialization started: requestId={}, accountId={}", requestId, accountId);

    repository.upsertOzon(accountId, requestId);
    repository.upsertWildberries(accountId, requestId);

    log.info("Warehouse materialization finished: requestId={}, accountId={}", requestId, accountId);
  }
}
