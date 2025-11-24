package io.datapulse.etl.service;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlMaterializationServiceImpl implements EtlMaterializationService {

  private final SalesFactMaterializationService salesFactMaterializationService;
  private final AccountConnectionService accountConnectionService;

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
      case SALES_FACT -> materializeSalesFactForAllMarketplaces(accountId, from, to, requestId);

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

  private void materializeSalesFactForAllMarketplaces(
      long accountId,
      LocalDate from,
      LocalDate to,
      String requestId
  ) {
    List<MarketplaceType> marketplaces =
        accountConnectionService.getActiveMarketplacesByAccountId(accountId);

    if (marketplaces.isEmpty()) {
      log.warn(
          "SalesFact materialization skipped: no active marketplaces for accountId={}, requestId={}",
          accountId,
          requestId
      );
      return;
    }

    marketplaces.forEach(marketplaceType -> salesFactMaterializationService.materialize(
        marketplaceType,
        accountId,
        from,
        to,
        requestId
    ));
  }
}
