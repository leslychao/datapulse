package io.datapulse.etl.event.impl.warehouse;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
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

  @Override
  public Snapshot<OzonWarehouseListRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to
  ) {
    return ozonAdapter.downloadWarehouseList(accountId);
  }
}
