package io.datapulse.etl.flow;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlSourceExecution;
import java.time.Duration;

public class RateLimitHandler {

  public static EtlSourceExecution apply(EtlSourceExecution execution) {
    if (execution.marketplace() == MarketplaceType.WILDBERRIES
        && execution.event() == MarketplaceEvent.REF_SYNC_WB_TARIFFS) {
      Duration delay = Duration.ofSeconds(65L);
      try {
        Thread.sleep(delay.toMillis());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    return execution;
  }
}
