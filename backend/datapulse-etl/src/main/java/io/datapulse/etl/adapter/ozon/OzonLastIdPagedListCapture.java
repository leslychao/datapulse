package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

/**
 * Shared {@code last_id} cursor pagination for Ozon Seller POST list endpoints (product list,
 * prices, stocks, attributes, etc.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonLastIdPagedListCapture {

    /** Called after each page once {@link OzonCursorPaging#afterPage} has been evaluated. */
    @FunctionalInterface
    public interface LastIdPageObserver {

        void onAfterPage(
                int capturedPageIndex,
                CaptureContext context,
                OzonCursorPaging.StringCursorPageOutcome outcome);
    }

    public static final LastIdPageObserver NO_OP = (capturedPageIndex, context, outcome) -> {};

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    /**
     * @param initialLastId resume cursor; {@code null} treated as empty string (first page)
     */
    public List<CaptureResult> captureAllPages(
            CaptureContext context,
            String clientId,
            String apiKey,
            String initialLastId,
            OzonLastIdListSpec spec,
            LastIdPageObserver observer) {
        List<CaptureResult> results = new ArrayList<>();
        String lastId = initialLastId == null ? "" : initialLastId;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            String currentLastId = lastId;
            Flux<DataBuffer> body = apiCaller.post(
                    spec.path(),
                    spec.bodyBuilder().apply(currentLastId),
                    context.connectionId(),
                    RateLimitGroup.OZON_DEFAULT,
                    clientId,
                    apiKey);

            PageCaptureResult page = pageCapture.capture(
                    body,
                    context,
                    pageNumber,
                    spec.cursorExtractor(),
                    null,
                    currentLastId.isEmpty() ? null : currentLastId);
            results.add(page.captureResult());

            lastId = page.cursor();
            var outcome = OzonCursorPaging.afterPage(lastId, currentLastId);
            observer.onAfterPage(pageNumber, context, outcome);
            if (outcome.stop()) {
                hasMore = false;
            }

            pageNumber++;
        }

        log.info(
                "Ozon {} capture completed: connectionId={}, totalPages={}",
                spec.logLabel(),
                context.connectionId(),
                results.size());
        return results;
    }

    public record OzonLastIdListSpec(
            String path,
            int pageLimit,
            JsonPathCursorExtractor cursorExtractor,
            String logLabel,
            Function<String, Map<String, Object>> bodyBuilder) {}
}
