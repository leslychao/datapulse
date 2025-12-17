package io.datapulse.etl.materialization.dim.product;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class ProductMaterializationHandler implements MaterializationHandler {

  private final DimProductRepository repository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.PRODUCT_DICT;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Product materialization started: requestId={}, accountId={}", requestId, accountId);

    repository.upsertOzon(accountId, requestId);
    repository.upsertWildberries(accountId, requestId);

    log.info("Product materialization finished: requestId={}, accountId={}", requestId, accountId);
  }
}
