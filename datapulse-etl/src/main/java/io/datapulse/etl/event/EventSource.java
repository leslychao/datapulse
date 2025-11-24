package io.datapulse.etl.event;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.marketplace.Snapshot;
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
