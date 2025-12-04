package io.datapulse.etl.integration.external;

import java.util.UUID;

public interface MarketplaceApiClient {

  String fetchEvent(UUID eventId);
}
