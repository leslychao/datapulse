package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.dim.category.CategoryMaterializationHandler;
import io.datapulse.etl.materialization.dim.tariff.TariffMaterializationHandler;
import io.datapulse.etl.materialization.dim.warehouse.WarehouseMaterializationHandler;
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
  private final CategoryMaterializationHandler categoryMaterializationHandler;
  private final TariffMaterializationHandler tariffMaterializationHandler;

  @Override
  @Transactional
  public void materialize(
      Long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo,
      String requestId
  ) {
    if (accountId == null) {
      log.warn(
          "ETL materialization skipped: null accountId, requestId={}, event={}, dateFrom={}, dateTo={}",
          requestId,
          event,
          dateFrom,
          dateTo
      );
      return;
    }

    log.info(
        "ETL materialization started: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        requestId,
        accountId,
        event,
        dateFrom,
        dateTo
    );

    switch (event) {
      case WAREHOUSE_DICT -> warehouseMaterializationHandler.materialize(accountId, requestId);
      case CATEGORY_DICT -> categoryMaterializationHandler.materialize(accountId, requestId);
      case COMMISSION_DICT -> tariffMaterializationHandler.materialize(accountId, requestId);
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
        dateFrom,
        dateTo
    );
  }
}
