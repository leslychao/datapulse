package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
public class OzonFbsOrdersReadAdapter {

    private static final String BASE_URL = "https://api-seller.ozon.ru";
    private static final String FBS_LIST_PATH = "/v3/posting/fbs/list";
    private static final int PAGE_LIMIT = 1000;

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context,
                                               String clientId, String apiKey,
                                               OffsetDateTime since, OffsetDateTime to) {
        List<CaptureResult> results = new ArrayList<>();
        long offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            rateLimiter.acquire(context.connectionId(), RateLimitGroup.OZON_DEFAULT).join();

            long currentOffset = offset;
            Flux<DataBuffer> body = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + FBS_LIST_PATH)
                    .header("Client-Id", clientId)
                    .header("Api-Key", apiKey)
                    .bodyValue(Map.of(
                            "dir", "ASC",
                            "filter", Map.of(
                                    "since", since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    "to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                            "limit", PAGE_LIMIT,
                            "offset", currentOffset,
                            "with", Map.of("analytics_data", true, "financial_data", true)))
                    .exchangeToFlux(response -> {
                        int status = response.statusCode().value();
                        rateLimiter.onResponse(context.connectionId(), RateLimitGroup.OZON_DEFAULT, status);
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

            if (page.captureResult().byteSize() < 200) {
                hasMore = false;
            }
        }

        log.info("Ozon FBS postings capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
