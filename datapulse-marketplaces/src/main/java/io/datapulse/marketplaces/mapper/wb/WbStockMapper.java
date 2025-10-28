package io.datapulse.marketplaces.mapper.wb;

import io.datapulse.domain.FulfillmentType;
import io.datapulse.domain.dto.StockDto;
import io.datapulse.marketplaces.dto.raw.wb.WbStockRaw;
import java.time.LocalDate;

public final class WbStockMapper {

  private WbStockMapper() {
  }

  public static StockDto toDto(LocalDate onDate, WbStockRaw r) {
    String sku = r.supplierArticle() != null ? r.supplierArticle() : String.valueOf(r.nmId());
    int available = r.quantity() == null ? 0 : r.quantity();
    int inWay =
        (r.inWayToClient() == null ? 0 : r.inWayToClient()) + (r.inWayFromClient() == null ? 0
            : r.inWayFromClient());
    return new StockDto(
        sku,
        onDate,
        r.lastChangeDate(),
        null,
        r.warehouseName(),
        FulfillmentType.FBS,
        available,
        0,
        inWay,
        null
    );
  }
}
