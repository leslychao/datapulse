package io.datapulse.etl.adapter.wb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
public class WbOrdersReadAdapter {

    private static final String ORDERS_PATH = "/api/v1/supplier/orders";

    private final WbApiCaller apiCaller;
    private final IntegrationProperties properties;
    private final StreamingPageCapture pageCapture;

    /**
     * Single response — no pagination. Date-range controlled by caller.
     */
    public CaptureResult capturePage(CaptureContext context, String apiToken,
                                     LocalDate dateFrom, int flag) {
        String baseUrl = properties.getWildberries().getStatisticsBaseUrl();
        Flux<DataBuffer> body = apiCaller.get(
                baseUrl + ORDERS_PATH,
                uriBuilder -> uriBuilder
                        .queryParam("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("flag", flag)
                        .build(),
                apiToken, context.connectionId(), RateLimitGroup.WB_STATISTICS);

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("WB orders capture completed: connectionId={}, dateFrom={}, byteSize={}",
                context.connectionId(), dateFrom, page.captureResult().byteSize());
        return page.captureResult();
    }
}
