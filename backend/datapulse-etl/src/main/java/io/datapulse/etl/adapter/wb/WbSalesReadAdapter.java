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
public class WbSalesReadAdapter {

    private static final String BASE_URL = "https://statistics-api.wildberries.ru";
    private static final String SALES_PATH = "/api/v1/supplier/sales";

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context, String apiToken,
                                     LocalDate dateFrom, int flag) {
        rateLimiter.acquire(context.connectionId(), RateLimitGroup.WB_STATISTICS).join();

        Flux<DataBuffer> body = webClientBuilder.build()
                .get()
                .uri(BASE_URL + SALES_PATH, uriBuilder -> uriBuilder
                        .queryParam("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("flag", flag)
                        .build())
                .header("Authorization", apiToken)
                .exchangeToFlux(response -> {
                    int status = response.statusCode().value();
                    rateLimiter.onResponse(context.connectionId(), RateLimitGroup.WB_STATISTICS, status);
                    if (response.statusCode().isError()) {
                        return response.createException().flatMapMany(Flux::error);
                    }
                    return response.bodyToFlux(DataBuffer.class);
                });

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("WB sales capture completed: connectionId={}, dateFrom={}, byteSize={}",
                context.connectionId(), dateFrom, page.captureResult().byteSize());
        return page.captureResult();
    }
}
