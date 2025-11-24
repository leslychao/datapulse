package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public interface EtlMaterializationService {

  void materialize(
      Long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to,
      String requestId
  );
}
