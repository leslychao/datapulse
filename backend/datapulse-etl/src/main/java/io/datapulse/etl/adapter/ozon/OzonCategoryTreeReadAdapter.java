package io.datapulse.etl.adapter.ozon;

import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonCategoryTreeReadAdapter {

    private static final String CATEGORY_TREE_PATH = "/v1/description-category/tree";

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public CaptureResult capturePage(CaptureContext context,
                                     String clientId, String apiKey) {
        Flux<DataBuffer> body = apiCaller.post(CATEGORY_TREE_PATH,
                Map.of("language", "DEFAULT"),
                context.connectionId(), RateLimitGroup.OZON_DEFAULT,
                clientId, apiKey);

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("Ozon category tree capture completed: connectionId={}, byteSize={}",
                context.connectionId(), page.captureResult().byteSize());
        return page.captureResult();
    }
}
