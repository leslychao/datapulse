package io.datapulse.etl.materialization.sales;

import io.datapulse.domain.MarketplaceType;
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
public final class SalesFactOzonMaterializationHandler implements MaterializationHandler {

  private final DimProductRepository dimProductRepository;
  private final SalesFactRepository salesFactRepository;

  private final ReturnsFactRepository returnsFactRepository;

  private final DimWarehouseRepository dimWarehouseRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.SALES_FACT;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Sales fact materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
    dimWarehouseRepository.upsertOzonFromPostings(accountId, requestId);
    dimWarehouseRepository.upsertOzonFromReturns(accountId, requestId);

    dimProductRepository.upsertOzonFromPostingsFbs(accountId, requestId);
    dimProductRepository.upsertOzonFromPostingsFbo(accountId, requestId);

    salesFactRepository.upsertOzonPostingsFbs(accountId, requestId);
    salesFactRepository.upsertOzonPostingsFbo(accountId, requestId);

    returnsFactRepository.upsertOzonReturns(accountId, requestId);

    log.info("Sales fact materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
