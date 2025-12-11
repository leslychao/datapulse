package io.datapulse.etl.event.impl.warehouse;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseSellerListRaw;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.WAREHOUSE_DICT,
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WB_WAREHOUSES_SELLER
)
public class WbWarehouseSellerEventSource implements EventSource {

  private final WbAdapter wbAdapter;

  @Override
  public Snapshot<WbWarehouseSellerListRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    return wbAdapter.downloadSellerWarehouses(accountId);
  }
}
