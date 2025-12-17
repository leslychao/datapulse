package io.datapulse.etl.materialization.dim.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMaterializationHandler {

  private final DimProductRepository repository;

  public void materialize(
      Long accountId,
      String requestId
  ) {
    repository.upsertOzon(accountId, requestId);
    repository.upsertWildberries(accountId, requestId);
  }
}
