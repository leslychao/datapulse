package io.datapulse.etl.adapter.wb;

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
public class WbWarehousesReadAdapter {

    private static final String OFFICES_PATH = "/api/v3/offices";

    private final WbApiCaller apiCaller;
    private final IntegrationProperties properties;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context, String apiToken) {
        String baseUrl = properties.getWildberries().getMarketplaceBaseUrl();
        Flux<DataBuffer> body = apiCaller.get(
                baseUrl + OFFICES_PATH,
                apiToken, context.connectionId(), RateLimitGroup.WB_MARKETPLACE);

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("WB warehouses capture completed: connectionId={}, byteSize={}",
                context.connectionId(), page.captureResult().byteSize());
        return page.captureResult();
    }
}
