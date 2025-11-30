package io.datapulse.etl.event;

import io.datapulse.domain.ValidationKeys;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.marketplaces.dto.Snapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public interface EventSource {

  @NotNull
  @Valid
  Snapshot<?> fetchSnapshot(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,

      @NotNull(message = ValidationKeys.ETL_EVENT_REQUIRED)
      MarketplaceEvent event,

      @NotNull(message = ValidationKeys.ETL_DATE_FROM_REQUIRED)
      LocalDate from,

      @NotNull(message = ValidationKeys.ETL_DATE_TO_REQUIRED)
      LocalDate to
  );
}
