package io.datapulse.etl.materialization;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;

public interface MaterializationHandler {

  MarketplaceEvent supportedEvent();

  MarketplaceType marketplace();

  void materialize(long accountId, String requestId);
}
