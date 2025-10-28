package io.datapulse.marketplaces.mapper.ozon;


import io.datapulse.domain.FulfillmentType;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.marketplaces.dto.raw.ozon.OzonSaleRaw;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class OzonSaleMapper {

  private OzonSaleMapper() {
  }

  public static SaleDto toDto(OzonSaleRaw r) {
    return new SaleDto(
        nzStr(r.sku()),
        r.posting_number(),
        r.offer_id(),
        FulfillmentType.FBO,
        r.status(),
        bool(r.is_cancelled()),
        bool(r.is_return()),
        r.created_at() != null ? r.created_at().toLocalDate() : LocalDate.now(),
        r.created_at(),
        r.in_process_at(),
        nzI(r.quantity()),
        r.price(),
        r.total_price(),
        nz(r.total_price()),
        BigDecimal.ZERO,
        nz(r.commission_amount()),
        nz(r.delivery_amount()),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
    );
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static int nzI(Integer v) {
    return v == null ? 0 : v;
  }

  private static boolean bool(Boolean b) {
    return Boolean.TRUE.equals(b);
  }

  private static String nzStr(String s) {
    return s == null ? "" : s;
  }
}
