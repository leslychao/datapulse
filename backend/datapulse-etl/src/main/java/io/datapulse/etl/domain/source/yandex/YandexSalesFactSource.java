package io.datapulse.etl.domain.source.yandex;

import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.YandexOrdersReadAdapter;
import io.datapulse.etl.adapter.yandex.YandexReturnsReadAdapter;
import io.datapulse.etl.adapter.yandex.dto.YandexOrder;
import io.datapulse.etl.adapter.yandex.dto.YandexReturn;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalOrderUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalReturnUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository.OfferSkuIds;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Yandex SALES_FACT captures:
 * <ol>
 *   <li>Orders — business-level endpoint (all campaigns in one call)</li>
 *   <li>Returns — campaign-level fan-out (per campaign from metadata)</li>
 * </ol>
 * Date range uses {@code wbFactDateFrom/To} (both are {@code LocalDate}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexSalesFactSource implements EventSource {

  private static final String ORDERS_SOURCE_ID = "YandexOrdersReadAdapter";
  private static final String RETURNS_SOURCE_ID = "YandexReturnsReadAdapter";

  private final YandexOrdersReadAdapter ordersAdapter;
  private final YandexReturnsReadAdapter returnsAdapter;
  private final YandexNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final CanonicalOrderUpsertRepository orderRepo;
  private final CanonicalReturnUpsertRepository returnRepo;
  private final SkuLookupRepository skuLookup;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.SALES_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());
    var dateFrom = ctx.wbFactDateFrom();
    var dateTo = ctx.wbFactDateTo();
    List<SubSourceResult> results = new ArrayList<>();

    var ordersCtx = CaptureContextFactory.build(ctx, eventType(), ORDERS_SOURCE_ID);
    List<CaptureResult> orderPages = ordersAdapter.captureAllPages(
        ordersCtx, apiKey, meta.businessId(), dateFrom, dateTo);

    results.add(subSourceRunner.processPages(
        ORDERS_SOURCE_ID, orderPages, YandexOrder.class,
        batch -> {
          var normalized = normalizer.normalizeOrders(batch);
          orderRepo.batchUpsert(normalized.stream()
              .map(item -> mapper.toOrder(item, ctx))
              .toList());
        }));

    List<Long> campaignIds = meta.campaignIds();
    if (campaignIds.isEmpty()) {
      log.warn("No Yandex campaigns in metadata, skipping returns: connectionId={}",
          ctx.connectionId());
      return results;
    }

    var skuCodeMap = skuLookup.findAllOfferBySellerSkuCode(ctx.workspaceId());

    var returnsCtx = CaptureContextFactory.build(ctx, eventType(), RETURNS_SOURCE_ID);
    List<CaptureResult> returnPages = returnsAdapter.captureAllPages(
        returnsCtx, apiKey, campaignIds, dateFrom, dateTo);

    results.add(subSourceRunner.processPages(
        RETURNS_SOURCE_ID, returnPages, YandexReturn.class,
        batch -> {
          var normalized = normalizer.normalizeReturns(batch);
          returnRepo.batchUpsert(normalized.stream()
              .map(item -> {
                OfferSkuIds ids = item.sellerSku() != null
                    ? skuCodeMap.get(item.sellerSku()) : null;
                return mapper.toReturn(item, ctx,
                    ids != null ? ids.offerId() : null,
                    ids != null ? ids.sellerSkuId() : null);
              })
              .toList());
        }));

    return results;
  }
}
