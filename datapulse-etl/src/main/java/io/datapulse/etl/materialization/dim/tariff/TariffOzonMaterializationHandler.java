package io.datapulse.etl.materialization.dim.tariff;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.DimTariffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class TariffOzonMaterializationHandler implements MaterializationHandler {

  private final DimTariffRepository dimTariffRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.TARIFF_DICT;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Tariff/commission materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());

    dimTariffRepository.upsertOzon(accountId, requestId);

    log.info("Tariff/commission materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
