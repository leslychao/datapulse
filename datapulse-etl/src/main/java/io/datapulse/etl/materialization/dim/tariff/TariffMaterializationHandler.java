package io.datapulse.etl.materialization.dim.tariff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TariffMaterializationHandler {

  private final DimTariffRepository dimTariffRepository;
  private final OzonProductCommissionRepository ozonProductCommissionRepository;

  public void materialize(
      Long accountId,
      String requestId
  ) {
    dimTariffRepository.upsertWildberries(accountId, requestId);
    ozonProductCommissionRepository.upsertOzon(accountId, requestId);
  }
}
