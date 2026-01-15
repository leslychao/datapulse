package io.datapulse.etl.service;

import io.datapulse.core.service.account.AccountConnectionService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.materialization.MaterializationHandlerRegistry;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlMaterializationServiceImpl implements EtlMaterializationService {

  private final AccountConnectionService accountConnectionService;
  private final MaterializationHandlerRegistry handlerRegistry;

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
          "ETL materialization skipped: accountId is null, requestId={}, event={}, dateFrom={}, dateTo={}",
          requestId, event, dateFrom, dateTo
      );
      return;
    }

    log.info(
        "ETL materialization started: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        requestId, accountId, event, dateFrom, dateTo
    );

    Set<MarketplaceType> activeMarketplaces = accountConnectionService
        .getActiveMarketplacesByAccountId(accountId);

    int executedHandlers = 0;

    for (MarketplaceType marketplace : activeMarketplaces) {
      MaterializationHandler handler = handlerRegistry.findFor(event, marketplace);
      if (handler == null) {
        continue;
      }

      executedHandlers++;

      log.info(
          "ETL materialization execute: requestId={}, accountId={}, event={}, marketplace={}, handler={}",
          requestId, accountId, event, marketplace, handler.getClass().getSimpleName()
      );

      handler.materialize(accountId, requestId);
    }

    if (executedHandlers == 0) {
      log.info(
          "ETL materialization skipped: no handler registered for event={}, requestId={}, accountId={}, activeMarketplaces={}",
          event, requestId, accountId, activeMarketplaces
      );
    } else {
      log.info(
          "ETL materialization completed: executedHandlers={}, requestId={}, accountId={}, event={}, activeMarketplaces={}",
          executedHandlers, requestId, accountId, event, activeMarketplaces
      );
    }

    log.info(
        "ETL materialization finished: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        requestId, accountId, event, dateFrom, dateTo
    );
  }
}
