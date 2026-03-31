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
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonFboOrdersReadAdapter {

    private static final String FBO_LIST_PATH = "/v2/posting/fbo/list";
    private static final int PAGE_LIMIT = 1000;

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context,
                                               String clientId, String apiKey,
                                               OffsetDateTime since, OffsetDateTime to) {
        List<CaptureResult> results = new ArrayList<>();
        long offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            long currentOffset = offset;
            Flux<DataBuffer> body = apiCaller.post(FBO_LIST_PATH,
                    Map.of(
                            "dir", "ASC",
                            "filter", Map.of(
                                    "since", since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    "to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                            "limit", PAGE_LIMIT,
                            "offset", currentOffset,
                            "with", Map.of("analytics_data", true, "financial_data", true)),
                    context.connectionId(), RateLimitGroup.OZON_DEFAULT,
                    clientId, apiKey);

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            offset += PAGE_LIMIT;
            pageNumber++;

            if (page.captureResult().byteSize() < 200) {
                hasMore = false;
            }
        }

        log.info("Ozon FBO postings capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
