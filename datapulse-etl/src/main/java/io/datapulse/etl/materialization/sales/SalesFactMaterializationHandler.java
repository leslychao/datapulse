package io.datapulse.etl.materialization.sales;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class SalesFactMaterializationHandler implements MaterializationHandler {

  private final SalesFactRepository repository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.SALES_FACT;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Sales fact materialization started: requestId={}, accountId={}", requestId, accountId);

    repository.upsertWildberries(accountId, requestId);
    repository.upsertOzonPostingsFbs(accountId, requestId);
    repository.upsertOzonFinanceTransactions(accountId, requestId);

    log.info("Sales fact materialization finished: requestId={}, accountId={}", requestId, accountId);
  }
}
