package io.datapulse.etl.materialization.inventory;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.InventoryFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class InventoryFactOzonMaterializationHandler implements MaterializationHandler {

  private final InventoryFactRepository inventoryFactRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.INVENTORY_FACT;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Inventory fact materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());

    inventoryFactRepository.upsertOzonAnalyticsStocks(accountId, requestId);
    inventoryFactRepository.upsertOzonProductInfoStocks(accountId, requestId);

    log.info("Inventory fact materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
