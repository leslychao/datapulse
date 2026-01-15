package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.DimWarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class WarehouseOzonMaterializationHandler implements MaterializationHandler {

  private final DimWarehouseRepository repository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.WAREHOUSE_DICT;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Warehouse materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());

    repository.upsertOzon(accountId, requestId);

    log.info("Warehouse materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
