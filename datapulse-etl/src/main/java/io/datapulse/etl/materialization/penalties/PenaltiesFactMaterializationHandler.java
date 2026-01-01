package io.datapulse.etl.materialization.penalties;

import static io.datapulse.etl.MarketplaceEvent.FACT_PENALTIES;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.PenaltiesFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class PenaltiesFactMaterializationHandler implements MaterializationHandler {

  private final PenaltiesFactRepository repository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return FACT_PENALTIES;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Penalties fact materialization started: requestId={}, accountId={}", requestId,
        accountId);

    repository.upsertOzon(accountId, requestId);
    repository.upsertWildberries(accountId, requestId);

    log.info("Penalties fact materialization finished: requestId={}, accountId={}", requestId,
        accountId);
  }
}
