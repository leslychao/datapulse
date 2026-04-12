package io.datapulse.etl.domain.source.yandex;

import java.util.List;

import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.YandexPromoReadAdapter;
import io.datapulse.etl.adapter.yandex.dto.YandexPromo;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalPromoCampaignUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Captures Yandex Market promo campaigns (business-level)
 * and upserts them into canonical {@code dim_promo_campaign}.
 */
@Component
@RequiredArgsConstructor
public class YandexPromoSyncSource implements EventSource {

  private static final String SOURCE_ID = "YandexPromoReadAdapter";

  private final YandexPromoReadAdapter adapter;
  private final YandexNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final CanonicalPromoCampaignUpsertRepository repository;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PROMO_SYNC;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);
    List<CaptureResult> pages = adapter.captureAllPages(
        captureCtx, apiKey, meta.businessId());

    SubSourceResult result = subSourceRunner.processPages(
        SOURCE_ID, pages, YandexPromo.class,
        batch -> repository.batchUpsert(batch.stream()
            .map(promo -> mapper.toPromoCampaign(
                normalizer.normalizePromo(promo), ctx))
            .toList()));
    return List.of(result);
  }
}
