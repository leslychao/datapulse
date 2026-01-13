package io.datapulse.etl.event.impl.category;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.WbSubjectListRaw;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    events = { MarketplaceEvent.CATEGORY_DICT },
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WB_SUBJECTS
)
public class WbSubjectEventSource implements EventSource {

  private final WbAdapter wbAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    Snapshot<WbSubjectListRaw> snapshot =
        wbAdapter.downloadSubjects(accountId);

    return List.of(snapshot);
  }
}
