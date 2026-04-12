package io.datapulse.etl.domain.source.yandex;

import java.util.List;

import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.MarketplaceType;
import org.springframework.stereotype.Component;

/**
 * Yandex Market categories are extracted from offer-mappings during
 * PRODUCT_DICT processing ({@code mapping.marketCategoryName}).
 * No dedicated category ingestion needed for MVP.
 */
@Component
public class YandexCategoryDictSource implements EventSource {

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.CATEGORY_DICT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext context) {
    return List.of(SubSourceResult.success("YandexCategoryDict", 0, 0));
  }
}
