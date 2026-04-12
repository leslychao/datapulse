package io.datapulse.etl.adapter.wb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.util.EmptyResponseException;
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
 * WB Statistics Sales: paginated via {@code lastChangeDate} cursor.
 * API returns up to ~80,000 rows per request; subsequent pages use
 * {@code lastChangeDate} from the last row as the next {@code dateFrom}.
 * Data retained for 90 days from sale date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WbSalesReadAdapter {

  private static final String SALES_PATH = "/api/v1/supplier/sales";
  private static final int MAX_PAGES = 50;
  private static final TailFieldExtractor CURSOR_EXTRACTOR =
      TailFieldExtractor.wbLastChangeDate();

  private final WbApiCaller apiCaller;
  private final IntegrationProperties properties;
  private final StreamingPageCapture pageCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken,
                                             LocalDate dateFrom, int flag) {
    List<CaptureResult> results = new ArrayList<>();
    String currentDateFrom = dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE);
    int pageNumber = 0;
    String baseUrl = properties.getWildberries().getStatisticsBaseUrl();

    while (pageNumber < MAX_PAGES) {
      String df = currentDateFrom;
      Flux<DataBuffer> body = apiCaller.get(
          baseUrl + SALES_PATH,
          uriBuilder -> uriBuilder
              .queryParam("dateFrom", df)
              .queryParam("flag", flag)
              .build(),
          apiToken, context.connectionId(), RateLimitGroup.WB_STATISTICS);

      PageCaptureResult page;
      try {
        page = pageCapture.capture(body, context, pageNumber, CURSOR_EXTRACTOR);
      } catch (EmptyResponseException e) {
        log.info("WB sales: end of data: connectionId={}, page={}",
            context.connectionId(), pageNumber);
        break;
      }

      results.add(page.captureResult());
      String cursor = page.cursor();
      if (cursor != null && !cursor.isEmpty()) {
        currentDateFrom = cursor;
      } else {
        break;
      }

      pageNumber++;
      log.debug("WB sales page captured: connectionId={}, page={}, cursor={}, byteSize={}",
          context.connectionId(), pageNumber, currentDateFrom, page.captureResult().byteSize());
    }

    log.info("WB sales capture completed: connectionId={}, dateFrom={}, totalPages={}",
        context.connectionId(), dateFrom, results.size());
    return results;
  }
}
