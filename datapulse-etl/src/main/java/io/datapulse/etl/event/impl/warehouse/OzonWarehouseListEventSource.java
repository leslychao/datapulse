package io.datapulse.etl.event.impl.warehouse;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.etl.event.util.SnapshotJsonArrayInspector;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseListRaw;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.WAREHOUSE,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_WAREHOUSE_LIST_OZON
)
public class OzonWarehouseListEventSource implements EventSource {

  private final OzonAdapter ozonAdapter;
  private final SnapshotJsonArrayInspector snapshotJsonArrayInspector;

  @Override
  public Snapshot<OzonWarehouseListRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to
  ) {
    Snapshot<OzonWarehouseListRaw> snapshot = ozonAdapter.downloadWarehouseList(accountId);

    if (snapshot.empty()) {
      return snapshot;
    }

    if (snapshotJsonArrayInspector.isArrayEmpty(snapshot.file(), "result")) {
      return Snapshot.empty(OzonWarehouseListRaw.class);
    }

    return snapshot;
  }

}
