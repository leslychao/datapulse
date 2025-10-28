package io.datapulse.marketplaces.mapper.ozon;

import io.datapulse.domain.FulfillmentType;
import io.datapulse.domain.dto.StockDto;
import io.datapulse.marketplaces.dto.raw.ozon.OzonStockRaw;

public final class OzonStockMapper {

  private OzonStockMapper() {
  }

  public static StockDto toDto(java.time.LocalDate onDate, OzonStockRaw r) {
    return new StockDto(
        r.sku(),
        onDate,
        r.captured_at(),
        r.warehouse_id(),
        r.warehouse_name(),
        FulfillmentType.FBO,
        nzI(r.qty_available()),
        nzI(r.qty_reserved()),
        nzI(r.qty_in_transit()),
        null
    );
  }

  private static int nzI(Integer v) {
    return v == null ? 0 : v;
  }
}
