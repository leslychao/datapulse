package io.datapulse.etl.service;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.etl.repository.ozon.OzonSalesFactRepository;
import io.datapulse.etl.repository.wb.WbSalesFactRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public final class SalesFactMaterializationService {

  private final AccountConnectionService accountConnectionService;
  private final OzonSalesFactRepository ozonSalesFactRepository;
  private final WbSalesFactRepository wbSalesFactRepository;

  public void materialize(
      long accountId,
      LocalDate dateFrom,
      LocalDate dateTo,
      String requestId
  ) {
    var marketplaces = accountConnectionService.getActiveMarketplacesByAccountId(accountId);

    if (marketplaces.isEmpty()) {
      log.warn(
          "SalesFact materialization skipped: no active marketplaces for accountId={}, requestId={}",
          accountId,
          requestId
      );
      return;
    }

    log.info(
        "SalesFact materialization started: requestId={}, accountId={}, marketplaces={}, from={}, to={}",
        requestId,
        accountId,
        marketplaces,
        dateFrom,
        dateTo
    );

    marketplaces.forEach(marketplace -> {
      switch (marketplace) {
        case OZON -> ozonSalesFactRepository.materializeSalesFact(
            accountId,
            dateFrom,
            dateTo,
            requestId
        );
        case WILDBERRIES -> wbSalesFactRepository.materializeSalesFact(
            accountId,
            dateFrom,
            dateTo,
            requestId
        );
        default -> log.warn(
            "SalesFact materialization skipped: unsupported marketplace={}, accountId={}, requestId={}",
            marketplace,
            accountId,
            requestId
        );
      }
    });

    log.info(
        "SalesFact materialization finished: requestId={}, accountId={}, marketplaces={}, from={}, to={}",
        requestId,
        accountId,
        marketplaces,
        dateFrom,
        dateTo
    );
  }
}
