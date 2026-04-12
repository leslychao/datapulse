package io.datapulse.etl.domain.source.yandex;

import java.util.List;

import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * No-op stub: Yandex Market has no supply (incoming shipment) endpoint
 * analogous to WB's supply API.
 */
@Slf4j
@Component
public class YandexSupplyFactSource implements EventSource {

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.SUPPLY_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    log.info("Yandex SUPPLY_FACT skipped (no-op stub): connectionId={}", ctx.connectionId());
    return List.of(SubSourceResult.success("YandexSupplyFact", 0, 0));
  }
}
