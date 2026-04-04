package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Shared offset/limit pagination for Ozon Seller list endpoints: duplicate-page guard, max page cap,
 * and small-response termination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonOffsetPagedListCapture {

    private final StreamingPageCapture pageCapture;

    /**
     * @param logLabel short label for logs, e.g. {@code FBO postings} / {@code FBS postings}
     * @param maxPages inclusive safety cap (use {@link OzonOffsetPaging#MAX_OFFSET_PAGES_PER_RUN} in
     *     production)
     * @param fetchPage supplies response body flux for {@code (offset, pageNumber)}
     */
    public List<CaptureResult> captureAllPages(
            CaptureContext context,
            int pageLimit,
            int maxPages,
            String logLabel,
            BiFunction<Long, Integer, Flux<DataBuffer>> fetchPage) {
        return captureAllPages(context, pageLimit, maxPages, logLabel, 0L, fetchPage);
    }

    /**
     * @param startOffset first API {@code offset} (resume from DLX checkpoint)
     */
    public List<CaptureResult> captureAllPages(
            CaptureContext context,
            int pageLimit,
            int maxPages,
            String logLabel,
            long startOffset,
            BiFunction<Long, Integer, Flux<DataBuffer>> fetchPage) {
        List<CaptureResult> results = new ArrayList<>();
        long offset = startOffset;
        int pageNumber = 0;
        boolean hasMore = true;
        String previousSha256 = null;

        while (hasMore) {
            if (pageNumber >= maxPages) {
                log.warn(
                    "Ozon {}: stopping pagination, max page cap reached (connectionId={}, pages={})",
                    logLabel,
                    context.connectionId(),
                    pageNumber);
                break;
            }

            long currentOffset = offset;
            Flux<DataBuffer> body = fetchPage.apply(currentOffset, pageNumber);

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, NoCursorExtractor.INSTANCE, currentOffset, null);
            CaptureResult captured = page.captureResult();

            if (OzonOffsetPaging.isRepeatedOffsetPage(previousSha256, captured.contentSha256())) {
                log.warn(
                    "Ozon {}: stopping pagination, identical response to previous page"
                        + " (offset={}, connectionId={})",
                    logLabel,
                    currentOffset,
                    context.connectionId());
                break;
            }

            results.add(captured);
            previousSha256 = captured.contentSha256();
            offset += pageLimit;
            pageNumber++;

            if (captured.byteSize() < OzonOffsetPaging.SMALL_PAGE_THRESHOLD_BYTES) {
                hasMore = false;
            }
        }

        log.info("Ozon {} capture completed: connectionId={}, totalPages={}",
                logLabel, context.connectionId(), results.size());
        return results;
    }
}
