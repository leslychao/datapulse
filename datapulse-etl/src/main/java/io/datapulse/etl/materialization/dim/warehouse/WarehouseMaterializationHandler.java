package io.datapulse.etl.materialization.dim.warehouse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseMaterializationHandler {

  private final DimWarehouseRepository repository;

  public void materialize(
      Long accountId,
      String requestId
  ) {
    repository.upsertOzon(accountId, requestId);
    repository.upsertWildberries(accountId, requestId);
  }
}
