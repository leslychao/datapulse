package io.datapulse.etl.adapter.wb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class WbStocksReadAdapter {

    private static final String BASE_URL = "https://seller-analytics-api.wildberries.ru";
    private static final String STOCKS_PATH = "/api/analytics/v1/stocks-report/wb-warehouses";
    private static final int PAGE_LIMIT = 10_000;

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken) {
        List<CaptureResult> results = new ArrayList<>();
        int offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            rateLimiter.acquire(context.connectionId(), RateLimitGroup.WB_ANALYTICS).join();

            int currentOffset = offset;
            Flux<DataBuffer> body = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + STOCKS_PATH)
                    .header("Authorization", apiToken)
                    .bodyValue(Map.of("limit", PAGE_LIMIT, "offset", currentOffset))
                    .exchangeToFlux(response -> {
                        int status = response.statusCode().value();
                        rateLimiter.onResponse(context.connectionId(), RateLimitGroup.WB_ANALYTICS, status);
                        if (response.statusCode().isError()) {
                            return response.createException().flatMapMany(Flux::error);
                        }
                        return response.bodyToFlux(DataBuffer.class);
                    });

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            offset += PAGE_LIMIT;
            pageNumber++;

            if (page.captureResult().byteSize() < 500) {
                hasMore = false;
            }

            log.debug("WB stocks page captured: connectionId={}, page={}", context.connectionId(), pageNumber);
        }

        log.info("WB stocks capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
