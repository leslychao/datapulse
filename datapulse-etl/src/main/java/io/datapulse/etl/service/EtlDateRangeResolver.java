package io.datapulse.etl.service;

import static io.datapulse.domain.MessageCodes.ETL_DATE_RANGE_INVALID;
import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlDateMode;
import io.datapulse.etl.dto.EtlDateRange;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class EtlDateRangeResolver {

  private static final int DEFAULT_LAST_DAYS = 30;

  public EtlDateRange resolve(
      MarketplaceEvent event,
      EtlDateMode mode,
      LocalDate dateFrom,
      LocalDate dateTo,
      Integer lastDays
  ) {
    EtlDateMode effectiveMode = resolveMode(event, mode, dateFrom, dateTo);

    return switch (effectiveMode) {
      case NONE -> new EtlDateRange(null, null);
      case RANGE -> resolveRange(dateFrom, dateTo);
      case LAST_DAYS -> resolveLastDaysRange(lastDays);
    };
  }

  private EtlDateMode resolveMode(
      MarketplaceEvent event,
      EtlDateMode mode,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    if (mode != null) {
      return mode;
    }
    if (dateFrom != null || dateTo != null) {
      return EtlDateMode.RANGE;
    }
    return defaultDateMode(event);
  }

  private EtlDateRange resolveRange(
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
    return new EtlDateRange(dateFrom, dateTo);
  }

  private EtlDateRange resolveLastDaysRange(Integer requestedLastDays) {
    int effectiveDays = resolveLastDays(requestedLastDays);
    LocalDate today = LocalDate.now();
    LocalDate calculatedFrom = today.minusDays(effectiveDays);
    return new EtlDateRange(calculatedFrom, today);
  }

  private EtlDateMode defaultDateMode(MarketplaceEvent event) {
    return EtlDateMode.NONE;
  }

  private int resolveLastDays(Integer requestedLastDays) {
    if (requestedLastDays != null && requestedLastDays > 0) {
      return requestedLastDays;
    }
    return DEFAULT_LAST_DAYS;
  }
}
