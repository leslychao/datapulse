package io.datapulse.etl.materialization.logistic;

import static io.datapulse.etl.MarketplaceEvent.FACT_LOGISTICS_COSTS;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.LogisticsFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class LogisticsFactMaterializationHandler implements MaterializationHandler {

  private final LogisticsFactRepository logisticsFactRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return FACT_LOGISTICS_COSTS;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Logistics fact materialization started: requestId={}, accountId={}", requestId,
        accountId);

    logisticsFactRepository.upsertOzon(accountId, requestId);
    logisticsFactRepository.upsertWildberries(accountId, requestId);

    log.info("Logistics fact materialization finished: requestId={}, accountId={}", requestId,
        accountId);
  }
}
