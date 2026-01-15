package io.datapulse.etl.materialization.sales;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.DimProductRepository;
import io.datapulse.etl.repository.ReturnsFactRepository;
import io.datapulse.etl.repository.SalesFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class SalesFactWildberriesMaterializationHandler implements MaterializationHandler {

  private final DimProductRepository dimProductRepository;
  private final SalesFactRepository salesFactRepository;

  private final ReturnsFactRepository returnsFactRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.SALES_FACT;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WILDBERRIES;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Sales fact materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());

    dimProductRepository.upsertWildberriesFromSales(accountId, requestId);

    salesFactRepository.upsertWildberries(accountId, requestId);

    returnsFactRepository.upsertWildberries(accountId, requestId);

    log.info("Sales fact materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
