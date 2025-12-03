package io.datapulse.etl.event.impl.warehouse;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.etl.event.util.SnapshotJsonArrayInspector;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseListRaw;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.WAREHOUSE,
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WAREHOUSE_LIST_WB
)
public class WbWarehouseListEventSource implements EventSource {

  private final WbAdapter wbAdapter;
  private final SnapshotJsonArrayInspector snapshotJsonArrayInspector;

  @Override
  public Snapshot<WbWarehouseListRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to
  ) {
    Snapshot<WbWarehouseListRaw> snapshot = wbAdapter.downloadWarehouseList(accountId);

    if (snapshot.empty()) {
      return snapshot;
    }

    if (snapshotJsonArrayInspector.isArrayEmpty(snapshot.file())) {
      return Snapshot.empty(WbWarehouseListRaw.class);
    }

    return snapshot;
  }

}
