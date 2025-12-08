package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dim.warehouse.WarehouseMaterializationHandler;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlMaterializationServiceImpl implements EtlMaterializationService {

  private final WarehouseMaterializationHandler warehouseMaterializationHandler;

  @Override
  @Transactional
  public void materialize(
      Long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to,
      String requestId
  ) {
    if (accountId == null) {
      log.warn(
          "ETL materialization skipped: null accountId, requestId={}, event={}, dateFrom={}, dateTo={}",
          requestId,
          event,
          from,
          to
      );
      return;
    }

    log.info(
        "ETL materialization started: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        requestId,
        accountId,
        event,
        from,
        to
    );

    switch (event) {
      case WAREHOUSE_DICT -> warehouseMaterializationHandler.materialize(accountId, requestId);
      default -> log.info(
          "No materialization configured for event={}, requestId={}, accountId={}",
          event,
          requestId,
          accountId
      );
    }

    log.info(
        "ETL materialization finished: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        requestId,
        accountId,
        event,
        from,
        to
    );
  }
}
