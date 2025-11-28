package io.datapulse.etl.event.impl;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseListRaw;
import java.time.LocalDate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.WAREHOUSE,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_WAREHOUSE_LIST_OZON
)
public final class OzonWarehouseListEventSource implements EventSource {

  private final OzonAdapter ozonAdapter;

  @Override
  @NonNull
  public Snapshot<OzonWarehouseListRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    return ozonAdapter.downloadWarehouseList(accountId);
  }
}
