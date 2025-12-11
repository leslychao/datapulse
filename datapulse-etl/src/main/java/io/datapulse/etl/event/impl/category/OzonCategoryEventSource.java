package io.datapulse.etl.event.impl.category;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.OzonCategoryTreeRaw;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.CATEGORY_DICT,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_CATEGORY_TREE
)
public class OzonCategoryEventSource implements EventSource {

  private final OzonAdapter ozonAdapter;

  @Override
  public Snapshot<OzonCategoryTreeRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    return ozonAdapter.downloadCategoryTree(accountId);
  }
}
