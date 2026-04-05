package io.datapulse.etl.adapter.wb;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.datapulse.etl.adapter.util.OffsetPagedCapture;
import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbPromoReadAdapter {

  private static final String PROMOTIONS_PATH = "/api/v1/calendar/promotions";
  private static final String NOMENCLATURES_PATH = "/api/v1/calendar/promotions/nomenclatures";
  private static final int PAGE_LIMIT = 1000;
  private static final int LOOKBACK_DAYS = 365;
  private static final int LOOKAHEAD_DAYS = 180;
  private static final int PROMO_SMALL_PAGE_THRESHOLD_BYTES = 500;

  private final WbApiCaller apiCaller;
  private final IntegrationProperties properties;
  private final StreamingPageCapture pageCapture;
  private final OffsetPagedCapture offsetPagedCapture;

  public List<CaptureResult> capturePromotions(CaptureContext context, String apiToken) {
    String baseUrl = properties.getWildberries().getPromoBaseUrl();
    OffsetDateTime now = OffsetDateTime.now();
    String startDt = now.minusDays(LOOKBACK_DAYS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    String endDt = now.plusDays(LOOKAHEAD_DAYS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        PROMO_SMALL_PAGE_THRESHOLD_BYTES,
        "WB promotions",
        (offset, pageNumber) -> apiCaller.get(
            baseUrl + PROMOTIONS_PATH,
            uriBuilder -> uriBuilder
                .queryParam("startDateTime", startDt)
                .queryParam("endDateTime", endDt)
                .queryParam("allPromo", true)
                .queryParam("limit", PAGE_LIMIT)
                .queryParam("offset", offset)
                .build(),
            apiToken, context.connectionId(), RateLimitGroup.WB_PROMO));
  }

  public List<CaptureResult> captureNomenclatures(CaptureContext context,
      String apiToken,
      long promotionId,
      boolean inAction) {
    String baseUrl = properties.getWildberries().getPromoBaseUrl();

    PageCaptureResult page = pageCapture.capture(
        apiCaller.get(
            baseUrl + NOMENCLATURES_PATH,
            uriBuilder -> uriBuilder
                .queryParam("promotionID", promotionId)
                .queryParam("inAction", inAction)
                .build(),
            apiToken, context.connectionId(), RateLimitGroup.WB_PROMO_NOMENCLATURES),
        context, 0, NoCursorExtractor.INSTANCE);

    log.debug("WB promo nomenclatures captured: connectionId={}, promotionId={},"
            + " inAction={}, byteSize={}",
        context.connectionId(), promotionId, inAction, page.captureResult().byteSize());
    return List.of(page.captureResult());
  }
}
