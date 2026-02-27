package io.datapulse.etl.service;

import static io.datapulse.domain.MessageCodes.ETL_DATE_RANGE_INVALID;
import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.DateMode;
import io.datapulse.etl.dto.DateRange;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class EtlDateRangeResolver {

  private static final int DEFAULT_LAST_DAYS = 30;

  public DateRange resolve(
      MarketplaceEvent event,
      DateMode mode,
      LocalDate dateFrom,
      LocalDate dateTo,
      Integer lastDays
  ) {
    DateMode effectiveMode = resolveMode(event, mode, dateFrom, dateTo);

    return switch (effectiveMode) {
      case NONE -> new DateRange(null, null);
      case RANGE -> resolveRange(dateFrom, dateTo);
      case LAST_DAYS -> resolveLastDaysRange(lastDays);
    };
  }

  private DateMode resolveMode(
      MarketplaceEvent event,
      DateMode mode,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    if (mode != null) {
      return mode;
    }
    if (dateFrom != null || dateTo != null) {
      return DateMode.RANGE;
    }
    return defaultDateMode(event);
  }

  private DateRange resolveRange(
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    if (dateFrom == null || dateTo == null) {
      throw new AppException(
          ETL_REQUEST_INVALID,
          "dateFrom,dateTo"
      );
    }
    if (dateFrom.isAfter(dateTo)) {
      throw new AppException(
          ETL_DATE_RANGE_INVALID,
          dateFrom,
          dateTo
      );
    }
    return new DateRange(dateFrom, dateTo);
  }

  private DateRange resolveLastDaysRange(Integer requestedLastDays) {
    int effectiveDays = resolveLastDays(requestedLastDays);
    LocalDate today = LocalDate.now();
    LocalDate calculatedFrom = today.minusDays(effectiveDays);
    return new DateRange(calculatedFrom, today);
  }

  private DateMode defaultDateMode(MarketplaceEvent event) {
    return DateMode.NONE;
  }

  private int resolveLastDays(Integer requestedLastDays) {
    if (requestedLastDays != null && requestedLastDays > 0) {
      return requestedLastDays;
    }
    return DEFAULT_LAST_DAYS;
  }
}
