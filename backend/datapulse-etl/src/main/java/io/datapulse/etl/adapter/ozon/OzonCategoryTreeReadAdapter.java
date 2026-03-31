package io.datapulse.etl.adapter.ozon;

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
public class OzonCategoryTreeReadAdapter {

    private static final String BASE_URL = "https://api-seller.ozon.ru";
    private static final String CATEGORY_TREE_PATH = "/v1/description-category/tree";

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context,
                                     String clientId, String apiKey) {
        rateLimiter.acquire(context.connectionId(), RateLimitGroup.OZON_DEFAULT).join();

        Flux<DataBuffer> body = webClientBuilder.build()
                .post()
                .uri(BASE_URL + CATEGORY_TREE_PATH)
                .header("Client-Id", clientId)
                .header("Api-Key", apiKey)
                .bodyValue(Map.of("language", "DEFAULT"))
                .exchangeToFlux(response -> {
                    int status = response.statusCode().value();
                    rateLimiter.onResponse(context.connectionId(), RateLimitGroup.OZON_DEFAULT, status);
                    if (response.statusCode().isError()) {
                        return response.createException().flatMapMany(Flux::error);
                    }
                    return response.bodyToFlux(DataBuffer.class);
                });

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("Ozon category tree capture completed: connectionId={}, byteSize={}",
                context.connectionId(), page.captureResult().byteSize());
        return page.captureResult();
    }
}
