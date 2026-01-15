package io.datapulse.etl.materialization.dim.category;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.materialization.MaterializationHandler;
import io.datapulse.etl.repository.DimCategoryRepository;
import io.datapulse.etl.repository.DimSubjectWbRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class CategoryWildberriesMaterializationHandler implements MaterializationHandler {

  private final DimCategoryRepository repository;
  private final DimSubjectWbRepository dimSubjectWbRepository;

  @Override
  public MarketplaceEvent supportedEvent() {
    return MarketplaceEvent.CATEGORY_DICT;
  }

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WILDBERRIES;
  }

  @Override
  public void materialize(long accountId, String requestId) {
    log.info("Category materialization started: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());

    repository.upsertWildberries(accountId, requestId);
    dimSubjectWbRepository.upsert(accountId, requestId);

    log.info("Category materialization finished: requestId={}, accountId={}, marketplace={}",
        requestId, accountId, marketplace());
  }
}
