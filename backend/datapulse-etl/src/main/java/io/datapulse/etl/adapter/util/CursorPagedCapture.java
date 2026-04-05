package io.datapulse.etl.adapter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.CursorExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Marketplace-agnostic cursor-based pagination loop. Each page returns an opaque
 * cursor string; stop when cursor is null, empty, or non-advancing (same as request).
 * <p>
 * Used by Ozon {@code last_id} endpoints, Ozon Returns (numeric {@code last_id}),
 * WB Catalog ({@code cursor.nmID}), and WB Finance ({@code rrdid}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CursorPagedCapture {

  /**
   * Outcome after one page: whether to stop, and whether it was because
   * the cursor did not advance (diagnostics).
   */
  public record CursorPageOutcome(boolean stop, boolean nonAdvancingCursor) {}

  @FunctionalInterface
  public interface CursorPageObserver {

    void onAfterPage(int capturedPageIndex, CaptureContext context, CursorPageOutcome outcome);
  }

  public static final CursorPageObserver NO_OP = (idx, ctx, out) -> {};

  private final StreamingPageCapture pageCapture;

  /**
   * @param spec       defines path, cursor extractor, body builder, log label
   * @param initialCursor resume cursor; {@code null} or empty treated as first page
   * @param observer   called after each page for diagnostics/logging
   */
  public List<CaptureResult> captureAllPages(
      CaptureContext context,
      String initialCursor,
      CursorPageSpec spec,
      CursorPageObserver observer) {
    List<CaptureResult> results = new ArrayList<>();
    String cursor = initialCursor == null ? "" : initialCursor;
    int pageNumber = 0;
    boolean hasMore = true;

    while (hasMore) {
      String currentCursor = cursor;
      Flux<DataBuffer> body = spec.fetchPage().apply(currentCursor, pageNumber);

      PageCaptureResult page = pageCapture.capture(
          body,
          context,
          pageNumber,
          spec.cursorExtractor(),
          null,
          currentCursor.isEmpty() ? null : currentCursor);
      results.add(page.captureResult());

      cursor = page.cursor();
      CursorPageOutcome outcome = evaluateCursor(cursor, currentCursor);
      observer.onAfterPage(pageNumber, context, outcome);
      if (outcome.stop()) {
        hasMore = false;
      }

      pageNumber++;
    }

    log.info("{} capture completed: connectionId={}, totalPages={}",
        spec.logLabel(), context.connectionId(), results.size());
    return results;
  }

  /**
   * Decides whether to stop based on response cursor vs request cursor.
   * Does not use response byte size — small valid pages must not truncate the crawl
   * (Ozon {@code last_id} pages can be legitimately small).
   */
  public static CursorPageOutcome evaluateCursor(
      String responseCursor, String requestCursor) {
    boolean sameCursor = Objects.equals(responseCursor, requestCursor);
    boolean stop = responseCursor == null || responseCursor.isEmpty() || sameCursor;
    boolean nonAdvancing =
        responseCursor != null && !responseCursor.isEmpty() && sameCursor;
    return new CursorPageOutcome(stop, nonAdvancing);
  }

  /**
   * Spec record defining how to fetch a page and extract the cursor.
   *
   * @param cursorExtractor how to extract cursor from the raw response file
   * @param logLabel        short label for log messages (e.g. "Ozon product list")
   * @param fetchPage       supplies response body for a given (cursor, pageNumber)
   */
  public record CursorPageSpec(
      CursorExtractor cursorExtractor,
      String logLabel,
      java.util.function.BiFunction<String, Integer, Flux<DataBuffer>> fetchPage) {}
}
