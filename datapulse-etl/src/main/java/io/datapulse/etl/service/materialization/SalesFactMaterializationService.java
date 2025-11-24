package io.datapulse.etl.service.materialization;

import io.datapulse.etl.repository.SalesFactMaterializationRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesFactMaterializationService {

  private final SalesFactMaterializationRepository repository;

  @Transactional
  public void materialize(
      long accountId,
      LocalDate dateFrom,
      LocalDate dateTo,
      String requestId
  ) {
    log.info(
        "SalesFact materialization started: requestId={}, accountId={}, from={}, to={}",
        requestId,
        accountId,
        dateFrom,
        dateTo
    );

    repository.materializeSalesFact(accountId, dateFrom, dateTo);

    log.info(
        "SalesFact materialization finished: requestId={}, accountId={}, from={}, to={}",
        requestId,
        accountId,
        dateFrom,
        dateTo
    );
  }
}
