package io.datapulse.etl.adapter.wb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.TailFieldExtractor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * WB Finance: rrdid cursor + HTTP 204 termination.
 * Uses {@link TailFieldExtractor} — reads last 32 KB of temp file
 * to find the last {@code rrd_id} value for cursor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WbFinanceReadAdapter {

    private static final String FINANCE_PATH = "/api/v5/supplier/reportDetailByPeriod";
    private static final int PAGE_LIMIT = 100_000;

    private static final TailFieldExtractor CURSOR_EXTRACTOR = TailFieldExtractor.wbRrdId();

    private final WbApiCaller apiCaller;
    private final IntegrationProperties properties;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken,
                                               LocalDate dateFrom, LocalDate dateTo) {
        List<CaptureResult> results = new ArrayList<>();
        long rrdid = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        String baseUrl = properties.getWildberries().getStatisticsBaseUrl();

        while (hasMore) {
            long currentRrdid = rrdid;
            Flux<DataBuffer> body = apiCaller.get(
                    baseUrl + FINANCE_PATH,
                    uriBuilder -> uriBuilder
                            .queryParam("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .queryParam("dateTo", dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .queryParam("limit", PAGE_LIMIT)
                            .queryParam("rrdid", currentRrdid)
                            .build(),
                    apiToken, context.connectionId(), RateLimitGroup.WB_STATISTICS);

            PageCaptureResult page;
            try {
                page = pageCapture.capture(body, context, pageNumber, CURSOR_EXTRACTOR);
            } catch (Exception e) {
                log.warn("WB finance page capture failed (possibly empty 204 response): connectionId={}, page={}",
                        context.connectionId(), pageNumber, e);
                hasMore = false;
                break;
            }

            if (page.captureResult().byteSize() < 10) {
                hasMore = false;
            } else {
                results.add(page.captureResult());
                String cursor = page.cursor();
                if (cursor != null && !cursor.isEmpty()) {
                    rrdid = Long.parseLong(cursor);
                } else {
                    hasMore = false;
                }
            }

            pageNumber++;
            log.debug("WB finance page captured: connectionId={}, page={}, rrdid={}, byteSize={}",
                    context.connectionId(), pageNumber, rrdid, page.captureResult().byteSize());
        }

        log.info("WB finance capture completed: connectionId={}, dateFrom={}, dateTo={}, totalPages={}",
                context.connectionId(), dateFrom, dateTo, results.size());
        return results;
    }
}
