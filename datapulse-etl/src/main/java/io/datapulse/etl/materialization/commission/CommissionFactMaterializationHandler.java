package io.datapulse.etl.materialization.commission;

import static io.datapulse.etl.MarketplaceEvent.FACT_COMMISSION;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.CommissionFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class CommissionFactMaterializationHandler implements MaterializationHandler {

  private final CommissionFactRepository commissionFactRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return FACT_COMMISSION;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info(
        "Commission fact materialization started: requestId={}, accountId={}",
        requestId,
        accountId
    );

    commissionFactRepository.upsertOzon(accountId, requestId);
    commissionFactRepository.upsertWildberries(accountId, requestId);

    log.info(
        "Commission fact materialization finished: requestId={}, accountId={}",
        requestId,
        accountId
    );
  }
}
