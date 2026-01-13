package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandlerRegistry;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlMaterializationServiceImpl implements EtlMaterializationService {

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

    handlerRegistry.findFor(event)
        .ifPresentOrElse(
            handler -> handler.materialize(accountId, requestId),
            () -> log.info(
                "ETL materialization skipped: no handler registered for event={}, requestId={}, accountId={}",
                event, requestId, accountId
            )
        );

    log.info(
        "ETL materialization finished: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        requestId, accountId, event, dateFrom, dateTo
    );
  }
}
