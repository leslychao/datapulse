package io.datapulse.etl.adapter.wb;

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
public class WbWarehousesReadAdapter {

    private static final String BASE_URL = "https://marketplace-api.wildberries.ru";
    private static final String OFFICES_PATH = "/api/v3/offices";

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context, String apiToken) {
        rateLimiter.acquire(context.connectionId(), RateLimitGroup.WB_MARKETPLACE).join();

        Flux<DataBuffer> body = webClientBuilder.build()
                .get()
                .uri(BASE_URL + OFFICES_PATH)
                .header("Authorization", apiToken)
                .exchangeToFlux(response -> {
                    int status = response.statusCode().value();
                    rateLimiter.onResponse(context.connectionId(), RateLimitGroup.WB_MARKETPLACE, status);
                    if (response.statusCode().isError()) {
                        return response.createException().flatMapMany(Flux::error);
                    }
                    return response.bodyToFlux(DataBuffer.class);
                });

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("WB warehouses capture completed: connectionId={}, byteSize={}",
                context.connectionId(), page.captureResult().byteSize());
        return page.captureResult();
    }
}
