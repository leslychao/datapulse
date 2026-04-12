package io.datapulse.etl.domain.source.yandex;

import java.util.List;

import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.YandexWarehousesReadAdapter;
import io.datapulse.etl.adapter.yandex.dto.YandexWarehouse;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.WarehouseUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Captures warehouse reference data from two Yandex endpoints:
 * fulfillment warehouses (FBY) and seller warehouses (FBS).
 * Both responses are captured as separate pages and processed together.
 */
@Component
@RequiredArgsConstructor
public class YandexWarehouseDictSource implements EventSource {

  private static final String SOURCE_ID = "YandexWarehousesReadAdapter";
  private static final String FULFILLMENT_TYPE = "FULFILLMENT";
  private static final String SELLER_TYPE = "SELLER";

  private final YandexWarehousesReadAdapter adapter;
  private final YandexNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final WarehouseUpsertRepository repository;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.WAREHOUSE_DICT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);
    List<CaptureResult> pages = adapter.capture(captureCtx, apiKey, meta.businessId());

    SubSourceResult result = subSourceRunner.processPages(
        SOURCE_ID, pages, YandexWarehouse.class,
        batch -> repository.batchUpsert(batch.stream()
            .map(wh -> mapper.toWarehouse(
                normalizer.normalizeWarehouse(wh, resolveWarehouseType(wh)),
                ctx))
            .toList()));
    return List.of(result);
  }

  private static String resolveWarehouseType(YandexWarehouse warehouse) {
    if (warehouse.address() != null) {
      return SELLER_TYPE;
    }
    return FULFILLMENT_TYPE;
  }
}
