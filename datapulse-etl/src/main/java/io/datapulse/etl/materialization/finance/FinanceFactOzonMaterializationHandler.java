package io.datapulse.etl.materialization.finance;

import static io.datapulse.etl.MarketplaceEvent.FACT_FINANCE;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.CommissionFactRepository;
import io.datapulse.etl.repository.DimWarehouseRepository;
import io.datapulse.etl.repository.FinanceFactRepository;
import io.datapulse.etl.repository.LogisticsFactRepository;
import io.datapulse.etl.repository.MarketingFactRepository;
import io.datapulse.etl.repository.PenaltiesFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class FinanceFactOzonMaterializationHandler implements MaterializationHandler {

  private final LogisticsFactRepository logisticsFactRepository;
  private final DimWarehouseRepository dimWarehouseRepository;

  private final CommissionFactRepository commissionFactRepository;

  private final MarketingFactRepository marketingFactRepository;

  private final PenaltiesFactRepository penaltiesFactRepository;
  private final FinanceFactRepository financeFactRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return FACT_FINANCE;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Finance fact materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());

    dimWarehouseRepository.upsertOzonFromTransactions(accountId, requestId);

    logisticsFactRepository.upsertOzon(accountId, requestId);

    commissionFactRepository.upsertOzon(accountId, requestId);

    marketingFactRepository.upsertOzon(accountId, requestId);

    penaltiesFactRepository.upsertOzon(accountId, requestId);

    financeFactRepository.upsertFromOzon(accountId, requestId);

    log.info("Finance fact materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
