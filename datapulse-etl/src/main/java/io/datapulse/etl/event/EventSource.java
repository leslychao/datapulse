package io.datapulse.etl.event;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.marketplaces.dto.Snapshot;
import java.time.LocalDate;
import lombok.NonNull;

public interface EventSource {

  @NonNull
  Snapshot<?> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  );
}
