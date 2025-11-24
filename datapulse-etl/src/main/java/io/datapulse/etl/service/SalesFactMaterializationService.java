package io.datapulse.etl.service;

import io.datapulse.domain.MarketplaceType;
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

  private final OzonSalesFactRepository ozonSalesFactRepository;
  private final WbSalesFactRepository wbSalesFactRepository;

  public void materialize(
      MarketplaceType marketplace,
      long accountId,
      LocalDate dateFrom,
      LocalDate dateTo,
      String requestId
  ) {
    log.info(
        "SalesFact materialization started: requestId={}, marketplace={}, accountId={}, from={}, to={}",
        requestId,
        marketplace,
        accountId,
        dateFrom,
        dateTo
    );

    ozonSalesFactRepository.materializeSalesFact(accountId, dateFrom, dateTo, requestId);
    wbSalesFactRepository.materializeSalesFact(accountId, dateFrom, dateTo, requestId);

    log.info(
        "SalesFact materialization finished: requestId={}, marketplace={}, accountId={}, from={}, to={}",
        requestId,
        marketplace,
        accountId,
        dateFrom,
        dateTo
    );
  }
}
