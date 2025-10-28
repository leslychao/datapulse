package io.datapulse.marketplaces.mapper.wb;

import io.datapulse.domain.FulfillmentType;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class WbSaleMapper {
  private WbSaleMapper() {}

  public static SaleDto toDto(WbSaleRaw r) {
    BigDecimal price = nz(r.priceWithDiscRub());
    BigDecimal revenue = nz(r.forPay());
    return new SaleDto(
        nzStr(r.supplierArticle()),
        null,
        nzStr(r.supplierArticle()),
        FulfillmentType.FBS,
        "OK",
        Boolean.TRUE.equals(r.isCancel()),
        Boolean.TRUE.equals(r.isReturn()),
        r.dateCreated()!=null ? r.dateCreated().toLocalDate() : LocalDate.now(),
        r.dateCreated(),
        null,
        nzI(r.quantity()),
        price,
        price,
        revenue,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
    );
  }

  private static BigDecimal nz(BigDecimal v){ return v==null?BigDecimal.ZERO:v; }
  private static int nzI(Integer v){ return v==null?0:v; }
  private static String nzStr(String s){ return s==null?"":s; }
}
