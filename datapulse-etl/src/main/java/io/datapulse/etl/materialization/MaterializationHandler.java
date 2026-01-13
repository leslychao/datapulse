package io.datapulse.etl.materialization;

import io.datapulse.etl.MarketplaceEvent;

public interface MaterializationHandler {

  MarketplaceEvent supportedEvent();

  void materialize(long accountId, String requestId);
}
