package io.datapulse.etl.event.impl;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseListRaw;
import java.time.LocalDate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("local")
@EtlSourceMeta(
    event = MarketplaceEvent.WAREHOUSE,
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WAREHOUSE_LIST_WB
)
public final class WbWarehouseListEventSource implements EventSource {

  private final WbAdapter wbAdapter;

  @Override
  @NonNull
  public Snapshot<WbWarehouseListRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    return wbAdapter.downloadWarehouseList(accountId);
  }
}
