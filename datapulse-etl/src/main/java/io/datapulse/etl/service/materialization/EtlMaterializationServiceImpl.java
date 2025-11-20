package io.datapulse.etl.service.materialization;

import io.datapulse.domain.MarketplaceEvent;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EtlMaterializationServiceImpl implements EtlMaterializationService {

  public void materialize(
      Long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to,
      String requestId
  ) {
    log.info(
        "Materialization executed: accountId={}, event={}, from={}, to={}, requestId={}",
        accountId, event, from, to, requestId
    );
  }
}
