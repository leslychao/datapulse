package io.datapulse.etl.event.impl.warehouse;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonClusterListRaw;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    events = { MarketplaceEvent.WAREHOUSE_DICT },
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_WAREHOUSES_FBO
)
public class OzonClustersEventSource implements EventSource {

  private final OzonAdapter ozonAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    Snapshot<OzonClusterListRaw> snapshot =
        ozonAdapter.downloadFboWarehouses(accountId);

    return List.of(snapshot);
  }
}
