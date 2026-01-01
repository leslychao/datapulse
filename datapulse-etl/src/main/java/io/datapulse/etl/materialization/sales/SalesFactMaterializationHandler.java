package io.datapulse.etl.materialization.sales;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.DimProductRepository;
import io.datapulse.etl.repository.DimWarehouseRepository;
import io.datapulse.etl.repository.ReturnsFactRepository;
import io.datapulse.etl.repository.SalesFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class SalesFactMaterializationHandler implements MaterializationHandler {

  private final DimProductRepository dimProductRepository;
  private final SalesFactRepository salesFactRepository;

  private final ReturnsFactRepository returnsFactRepository;

  private final DimWarehouseRepository dimWarehouseRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.SALES_FACT;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Sales fact materialization started: requestId={}, accountId={}", requestId,
        accountId);

    dimProductRepository.upsertOzonFromPostingsFbs(accountId, requestId);
    dimProductRepository.upsertOzonFromPostingsFbo(accountId, requestId);
    dimProductRepository.upsertWildberriesFromSales(accountId, requestId);

    salesFactRepository.upsertWildberries(accountId, requestId);
    salesFactRepository.upsertOzonPostingsFbs(accountId, requestId);
    salesFactRepository.upsertOzonPostingsFbo(accountId, requestId);

    returnsFactRepository.upsertWildberries(accountId, requestId);
    returnsFactRepository.upsertOzonReturns(accountId, requestId);

    dimWarehouseRepository.upsertOzonFromPostings(accountId, requestId);

    log.info("Sales fact materialization finished: requestId={}, accountId={}", requestId,
        accountId);
  }
}
