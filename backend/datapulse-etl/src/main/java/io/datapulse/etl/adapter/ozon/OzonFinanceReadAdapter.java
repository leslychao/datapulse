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
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonFinanceReadAdapter {

    private static final String FINANCE_PATH = "/v3/finance/transaction/list";
    private static final int PAGE_SIZE = 1000;

    private static final JsonPathCursorExtractor PAGE_COUNT_EXTRACTOR =
            new JsonPathCursorExtractor("result.page_count");

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context,
                                               String clientId, String apiKey,
                                               OffsetDateTime dateFrom, OffsetDateTime dateTo,
                                               int startPageInclusive) {
        List<CaptureResult> results = new ArrayList<>();
        int page = Math.max(1, startPageInclusive);
        int totalPages = Integer.MAX_VALUE;

        while (page <= totalPages) {
            int currentPage = page;
            Flux<DataBuffer> body = apiCaller.post(FINANCE_PATH,
                    Map.of(
                            "filter", Map.of(
                                    "date", Map.of(
                                            "from", dateFrom.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                            "to", dateTo.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                                    "operation_type", List.of(),
                                    "posting_number", "",
                                    "transaction_type", "all"),
                            "page", currentPage,
                            "page_size", PAGE_SIZE),
                    context.connectionId(), RateLimitGroup.OZON_DEFAULT,
                    clientId, apiKey);

            int pageNumber = page - 1;
            PageCaptureResult captured = pageCapture.capture(
                    body, context, pageNumber, PAGE_COUNT_EXTRACTOR, null,
                    String.valueOf(currentPage));
            results.add(captured.captureResult());

            String pageCountStr = captured.cursor();
            if (pageCountStr != null && !pageCountStr.isEmpty()) {
                totalPages = Integer.parseInt(pageCountStr);
            }

            page++;
            log.debug("Ozon finance page captured: connectionId={}, page={}/{}, byteSize={}",
                    context.connectionId(), page - 1, totalPages, captured.captureResult().byteSize());
        }

        log.info("Ozon finance capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
