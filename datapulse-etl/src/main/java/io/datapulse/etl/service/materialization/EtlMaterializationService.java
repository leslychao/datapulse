package io.datapulse.etl.service.materialization;

import io.datapulse.domain.MarketplaceEvent;
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
