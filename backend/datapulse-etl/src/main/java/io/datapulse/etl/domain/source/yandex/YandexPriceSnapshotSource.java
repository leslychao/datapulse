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
 * No-op: Yandex prices are embedded in offer-mappings ({@code offer.basicPrice})
 * and already captured as dual output in {@link YandexProductDictSource}.
 * A separate PRICE_SNAPSHOT call is unnecessary.
 */
@Slf4j
@Component
public class YandexPriceSnapshotSource implements EventSource {

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PRICE_SNAPSHOT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    log.debug("Yandex PRICE_SNAPSHOT skipped (dual-output from PRODUCT_DICT): connectionId={}",
        ctx.connectionId());
    return List.of(SubSourceResult.success("YandexPriceSnapshot", 0, 0));
  }
}
