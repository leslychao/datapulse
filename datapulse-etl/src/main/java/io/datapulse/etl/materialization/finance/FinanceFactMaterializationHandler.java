package io.datapulse.etl.materialization.finance;

import static io.datapulse.etl.MarketplaceEvent.FACT_FINANCE;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.FinanceFactRepository;
import io.datapulse.etl.repository.MarketingFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class FinanceFactMaterializationHandler implements MaterializationHandler {

  private final FinanceFactRepository repository;

  private final MarketingFactRepository marketingFactRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return FACT_FINANCE;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Finance fact materialization started: requestId={}, accountId={}", requestId,
        accountId);

    marketingFactRepository.upsertOzon(accountId, requestId);
    marketingFactRepository.upsertWildberries(accountId, requestId);
    
    repository.upsertFromOzon(accountId, requestId);
    repository.upsertFromWildberries(accountId, requestId);

    log.info("Finance fact materialization finished: requestId={}, accountId={}", requestId,
        accountId);
  }
}
