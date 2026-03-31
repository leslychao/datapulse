package io.datapulse.etl.adapter.wb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
public class WbReturnsReadAdapter {

    private static final String RETURNS_PATH = "/api/v1/analytics/goods-return";

    private final WbApiCaller apiCaller;
    private final IntegrationProperties properties;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context, String apiToken,
                                     LocalDate dateFrom, LocalDate dateTo) {
        String baseUrl = properties.getWildberries().getAnalyticsBaseUrl();
        Flux<DataBuffer> body = apiCaller.get(
                baseUrl + RETURNS_PATH,
                uriBuilder -> uriBuilder
                        .queryParam("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("dateTo", dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .build(),
                apiToken, context.connectionId(), RateLimitGroup.WB_ANALYTICS);

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("WB returns capture completed: connectionId={}, dateFrom={}, dateTo={}, byteSize={}",
                context.connectionId(), dateFrom, dateTo, page.captureResult().byteSize());
        return page.captureResult();
    }
}
