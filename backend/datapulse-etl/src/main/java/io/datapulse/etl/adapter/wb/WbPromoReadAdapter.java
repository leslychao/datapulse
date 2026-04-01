package io.datapulse.etl.adapter.wb;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbPromoReadAdapter {

    private static final String PROMOTIONS_PATH = "/api/v1/calendar/promotions";
    private static final String NOMENCLATURES_PATH = "/api/v1/calendar/promotions/nomenclatures";
    private static final int PAGE_LIMIT = 1000;
    private static final int LOOKBACK_DAYS = 365;
    private static final int LOOKAHEAD_DAYS = 180;

    private final WbApiCaller apiCaller;
    private final IntegrationProperties properties;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> capturePromotions(CaptureContext context, String apiToken) {
        List<CaptureResult> results = new ArrayList<>();
        int offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        String baseUrl = properties.getWildberries().getPromoBaseUrl();
        OffsetDateTime now = OffsetDateTime.now();
        String startDt = now.minusDays(LOOKBACK_DAYS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endDt = now.plusDays(LOOKAHEAD_DAYS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        while (hasMore) {
            int currentOffset = offset;
            Flux<DataBuffer> body = apiCaller.get(
                    baseUrl + PROMOTIONS_PATH,
                    uriBuilder -> uriBuilder
                            .queryParam("startDateTime", startDt)
                            .queryParam("endDateTime", endDt)
                            .queryParam("allPromo", true)
                            .queryParam("limit", PAGE_LIMIT)
                            .queryParam("offset", currentOffset)
                            .build(),
                    apiToken, context.connectionId(), RateLimitGroup.WB_PROMO);

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            offset += PAGE_LIMIT;
            pageNumber++;
            hasMore = page.captureResult().byteSize() > 500;

            log.debug("WB promotions page captured: connectionId={}, page={}, byteSize={}",
                    context.connectionId(), pageNumber, page.captureResult().byteSize());
        }

        log.info("WB promotions capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }

    public List<CaptureResult> captureNomenclatures(CaptureContext context,
                                                    String apiToken,
                                                    long promotionId,
                                                    boolean inAction) {
        List<CaptureResult> results = new ArrayList<>();
        String baseUrl = properties.getWildberries().getPromoBaseUrl();

        Flux<DataBuffer> body = apiCaller.get(
                baseUrl + NOMENCLATURES_PATH,
                uriBuilder -> uriBuilder
                        .queryParam("promotionID", promotionId)
                        .queryParam("inAction", inAction)
                        .build(),
                apiToken, context.connectionId(), RateLimitGroup.WB_PROMO_NOMENCLATURES);

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);
        results.add(page.captureResult());

        log.debug("WB promo nomenclatures captured: connectionId={}, promotionId={}, inAction={}, byteSize={}",
                context.connectionId(), promotionId, inAction, page.captureResult().byteSize());
        return results;
    }
}
