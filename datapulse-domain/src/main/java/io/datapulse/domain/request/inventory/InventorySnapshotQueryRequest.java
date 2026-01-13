package io.datapulse.domain.request.inventory;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public record InventorySnapshotQueryRequest(

    MarketplaceType marketplace,

    LocalDate fromDate,
    LocalDate toDate,

    String sourceProductId,
    Long warehouseId
) {

  @AssertTrue(message = ValidationKeys.DATE_RANGE_INVALID)
  public boolean isDateRangeValid() {
    if (fromDate == null || toDate == null) {
      return true;
    }
    return !fromDate.isAfter(toDate);
  }
}
