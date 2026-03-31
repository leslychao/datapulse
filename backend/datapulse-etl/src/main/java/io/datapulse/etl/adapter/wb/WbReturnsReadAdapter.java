package io.datapulse.etl.adapter.wb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbReturnsReadAdapter {

    private static final String BASE_URL = "https://seller-analytics-api.wildberries.ru";
    private static final String RETURNS_PATH = "/api/v1/analytics/goods-return";

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context, String apiToken,
                                     LocalDate dateFrom, LocalDate dateTo) {
        rateLimiter.acquire(context.connectionId(), RateLimitGroup.WB_ANALYTICS).join();

        Flux<DataBuffer> body = webClientBuilder.build()
                .get()
                .uri(BASE_URL + RETURNS_PATH, uriBuilder -> uriBuilder
                        .queryParam("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("dateTo", dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .build())
                .header("Authorization", apiToken)
                .exchangeToFlux(response -> {
                    int status = response.statusCode().value();
                    rateLimiter.onResponse(context.connectionId(), RateLimitGroup.WB_ANALYTICS, status);
                    if (response.statusCode().isError()) {
                        return response.createException().flatMapMany(Flux::error);
                    }
                    return response.bodyToFlux(DataBuffer.class);
                });

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("WB returns capture completed: connectionId={}, dateFrom={}, dateTo={}, byteSize={}",
                context.connectionId(), dateFrom, dateTo, page.captureResult().byteSize());
        return page.captureResult();
    }
}
