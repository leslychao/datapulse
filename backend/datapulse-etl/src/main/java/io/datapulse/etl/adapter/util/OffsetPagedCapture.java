package io.datapulse.etl.adapter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

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
 * Marketplace-agnostic offset/limit pagination loop with three safety guards:
 * <ol>
 *   <li>SHA-256 duplicate detection — stops if response body is byte-identical to previous page</li>
 *   <li>Max page cap — hard limit on pages per run (prevents infinite loops)</li>
 *   <li>Small page threshold — stops when response is smaller than threshold bytes</li>
 * </ol>
 * Used by both WB and Ozon offset-based adapters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffsetPagedCapture {

  public static final int DEFAULT_MAX_PAGES = 5_000;
  public static final int DEFAULT_SMALL_PAGE_THRESHOLD_BYTES = 200;

  private final StreamingPageCapture pageCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context,
      int pageLimit,
      String logLabel,
      BiFunction<Long, Integer, Flux<DataBuffer>> fetchPage) {
    return captureAllPages(
        context, pageLimit, DEFAULT_MAX_PAGES, DEFAULT_SMALL_PAGE_THRESHOLD_BYTES,
        logLabel, 0L, fetchPage);
  }

  public List<CaptureResult> captureAllPages(
      CaptureContext context,
      int pageLimit,
      int maxPages,
      int smallPageThresholdBytes,
      String logLabel,
      BiFunction<Long, Integer, Flux<DataBuffer>> fetchPage) {
    return captureAllPages(
        context, pageLimit, maxPages, smallPageThresholdBytes, logLabel, 0L, fetchPage);
  }

  /**
   * @param startOffset resume offset (e.g. from DLX checkpoint)
   */
  public List<CaptureResult> captureAllPages(
      CaptureContext context,
      int pageLimit,
      int maxPages,
      int smallPageThresholdBytes,
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
            "{}: stopping pagination, max page cap reached (connectionId={}, pages={})",
            logLabel, context.connectionId(), pageNumber);
        break;
      }

      long currentOffset = offset;
      Flux<DataBuffer> body = fetchPage.apply(currentOffset, pageNumber);

      PageCaptureResult page = pageCapture.capture(
          body, context, pageNumber, NoCursorExtractor.INSTANCE, currentOffset, null);
      CaptureResult captured = page.captureResult();

      if (isRepeatedPage(previousSha256, captured.contentSha256())) {
        log.warn(
            "{}: stopping pagination, identical response to previous page"
                + " (offset={}, connectionId={})",
            logLabel, currentOffset, context.connectionId());
        break;
      }

      results.add(captured);
      previousSha256 = captured.contentSha256();
      offset += pageLimit;
      pageNumber++;

      if (captured.byteSize() < smallPageThresholdBytes) {
        hasMore = false;
      }
    }

    log.info("{} capture completed: connectionId={}, totalPages={}",
        logLabel, context.connectionId(), results.size());
    return results;
  }

  private static boolean isRepeatedPage(
      String previousContentSha256, String currentContentSha256) {
    return previousContentSha256 != null
        && currentContentSha256 != null
        && Objects.equals(previousContentSha256, currentContentSha256);
  }
}
