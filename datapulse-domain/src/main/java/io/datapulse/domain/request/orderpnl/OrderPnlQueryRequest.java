package io.datapulse.domain.request.orderpnl;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public record OrderPnlQueryRequest(

    MarketplaceType sourcePlatform,

    LocalDate dateFrom,

    LocalDate dateTo,

    Boolean isReturned,

    Boolean hasPenalties
) {

  @AssertTrue(message = ValidationKeys.DATE_RANGE_INVALID)
  public boolean isDateRangeValid() {
    if (dateFrom == null || dateTo == null) {
      return true;
    }
    return !dateFrom.isAfter(dateTo);
  }
}
