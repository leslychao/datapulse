package io.datapulse.etl.flow.core.model;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.event.EventSource;
import java.time.LocalDate;
import java.util.Objects;

public record ExecutionDescriptor(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    EventWindow window,
    MarketplaceType marketplace,
    String sourceId,
    String rawTable,
    EventSource source
) {
  public ExecutionDescriptor {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(marketplace, "marketplace");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(rawTable, "rawTable");
    Objects.requireNonNull(source, "source");
  }

  public LocalDate from() {
    return window.from();
  }

  public LocalDate to() {
    return window.to();
  }
}
