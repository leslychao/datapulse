package io.datapulse.etl.adapter.yandex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.adapter.yandex.dto.YandexRealizationReportRow;
import io.datapulse.etl.adapter.yandex.dto.YandexServicesReportRow;
import io.datapulse.etl.config.EtlProperties;
import io.datapulse.etl.domain.CaptureContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Orchestrates Yandex Market async finance reports:
 * <ol>
 *   <li>United Marketplace Services report — commission/logistics/service fees</li>
 *   <li>Goods Realization report — realization data with item-level financials</li>
 * </ol>
 * Both reports follow the async flow: generate → poll → download → parse.
 * Report structure may change without notice (F-6) — DTOs use lenient parsing.
 */
@Slf4j
@Service
public class YandexFinanceReportReadAdapter {

  private static final String SERVICES_REPORT_PATH =
      "/v2/reports/united-marketplace-services/generate";
  private static final String REALIZATION_REPORT_PATH =
      "/v2/reports/goods-realization/generate";

  private final AsyncReportCapture asyncReportCapture;

  public YandexFinanceReportReadAdapter(
      YandexApiCaller apiCaller,
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      EtlProperties etlProperties) {
    this.asyncReportCapture = new AsyncReportCapture(
        apiCaller, webClientBuilder, objectMapper, etlProperties);
  }

  public List<YandexServicesReportRow> captureServicesReport(
      String apiKey, long connectionId, long businessId,
      LocalDate dateFrom, LocalDate dateTo) {
    log.info("Yandex services report: requesting for businessId={}, period={}-{}, connectionId={}",
        businessId, dateFrom, dateTo, connectionId);

    Map<String, Object> body = Map.of(
        "businessId", businessId,
        "dateFrom", dateFrom.toString(),
        "dateTo", dateTo.toString());

    return asyncReportCapture.captureReport(
        SERVICES_REPORT_PATH, body, apiKey, connectionId,
        YandexServicesReportRow.class);
  }

  public List<YandexRealizationReportRow> captureRealizationReport(
      String apiKey, long connectionId, long businessId,
      int year, int month) {
    log.info("Yandex realization report: requesting for businessId={}, period={}-{}, connectionId={}",
        businessId, year, month, connectionId);

    Map<String, Object> body = Map.of(
        "businessId", businessId,
        "year", year,
        "month", month);

    return asyncReportCapture.captureReport(
        REALIZATION_REPORT_PATH, body, apiKey, connectionId,
        YandexRealizationReportRow.class);
  }

  /**
   * Captures both finance reports for a given period.
   * For initial load: called per month from EventSource.
   * For incremental: covers current + previous month.
   */
  public void captureFinanceForPeriod(
      CaptureContext context, String apiKey, long businessId,
      LocalDate from, LocalDate to) {
    long connectionId = context.connectionId();

    log.info("Yandex finance capture: starting for businessId={}, period={}-{}, connectionId={}",
        businessId, from, to, connectionId);

    List<YandexServicesReportRow> servicesRows = captureServicesReport(
        apiKey, connectionId, businessId, from, to);
    log.info("Yandex services report captured: connectionId={}, rows={}",
        connectionId, servicesRows.size());

    List<MonthPeriod> months = splitIntoMonths(from, to);
    List<YandexRealizationReportRow> allRealizationRows = new ArrayList<>();

    for (MonthPeriod month : months) {
      List<YandexRealizationReportRow> monthRows = captureRealizationReport(
          apiKey, connectionId, businessId, month.year(), month.month());
      allRealizationRows.addAll(monthRows);
    }

    log.info("Yandex realization report captured: connectionId={}, months={}, totalRows={}",
        connectionId, months.size(), allRealizationRows.size());

    // TODO: normalize finance rows through YandexNormalizer once real data format is verified
    log.info("Yandex finance capture completed: connectionId={}, servicesRows={}, realizationRows={}",
        connectionId, servicesRows.size(), allRealizationRows.size());
  }

  static List<MonthPeriod> splitIntoMonths(LocalDate from, LocalDate to) {
    List<MonthPeriod> months = new ArrayList<>();
    LocalDate cursor = from.withDayOfMonth(1);
    LocalDate end = to.withDayOfMonth(1);

    while (!cursor.isAfter(end)) {
      months.add(new MonthPeriod(cursor.getYear(), cursor.getMonthValue()));
      cursor = cursor.plusMonths(1);
    }

    return months;
  }

  record MonthPeriod(int year, int month) {}
}
