package io.datapulse.etl.materialization.dim.tariff;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class TariffMaterializationHandler implements MaterializationHandler {

  private final DimTariffRepository dimTariffRepository;
  private final OzonProductCommissionRepository ozonProductCommissionRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.COMMISSION_DICT;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Tariff/commission materialization started: requestId={}, accountId={}", requestId, accountId);

    dimTariffRepository.upsertWildberries(accountId, requestId);
    ozonProductCommissionRepository.upsertOzon(accountId, requestId);

    log.info("Tariff/commission materialization finished: requestId={}, accountId={}", requestId, accountId);
  }
}
