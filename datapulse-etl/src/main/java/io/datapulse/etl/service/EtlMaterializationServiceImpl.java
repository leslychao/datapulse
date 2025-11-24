package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlMaterializationServiceImpl implements EtlMaterializationService {

  private final SalesFactMaterializationService salesFactMaterializationService;

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
          "ETL materialization skipped: null accountId, requestId={}, event={}, from={}, to={}",
          requestId,
          event,
          from,
          to
      );
      return;
    }

    log.info(
        "ETL materialization started: requestId={}, accountId={}, event={}, from={}, to={}",
        requestId,
        accountId,
        event,
        from,
        to
    );

    switch (event) {
      case SALES_FACT -> materializeSalesFact(accountId, from, to, requestId);
      // другие события добавишь по мере появления других процедур:
      // case STOCK_FACT -> ...
      // case PRICE_FACT -> ...
      default -> log.info(
          "No materialization configured for event={}, requestId={}, accountId={}",
          event,
          requestId,
          accountId
      );
    }

    log.info(
        "ETL materialization finished: requestId={}, accountId={}, event={}, from={}, to={}",
        requestId,
        accountId,
        event,
        from,
        to
    );
  }

  private void materializeSalesFact(
      long accountId,
      LocalDate from,
      LocalDate to,
      String requestId
  ) {
    salesFactMaterializationService.materialize(accountId, from, to, requestId);
  }
}
