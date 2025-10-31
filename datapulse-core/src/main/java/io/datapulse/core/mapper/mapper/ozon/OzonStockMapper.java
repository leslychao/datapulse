package io.datapulse.core.mapper.mapper.ozon;

import io.datapulse.domain.dto.StockDto;
import io.datapulse.domain.dto.raw.ozon.OzonStockRaw;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring", imports = BigDecimal.class)
public abstract class OzonStockMapper {

  /** Прямое преобразование пары (Item, Stock) в один StockDto */
  @Mappings({
      // Идентификаторы
      @Mapping(target = "sku", expression = "java(toStringSafe(item.sku()))"),
      @Mapping(target = "offerId", source = "item.offer_id"),
      @Mapping(target = "productId", expression = "java(toStringSafe(item.product_id()))"),

      // Склад
      @Mapping(target = "warehouseId", expression = "java(toStringSafe(stock.warehouse_id()))"),
      @Mapping(target = "warehouseName", ignore = true), // в этом методе имени нет

      // Остатки
      @Mapping(target = "quantityAvailable", source = "stock.present"),
      @Mapping(target = "quantityReserved", source = "stock.reserved"),
      @Mapping(target = "quantityInTransitToClient", ignore = true),
      @Mapping(target = "quantityInTransitFromClient", ignore = true),
      @Mapping(target = "quantityTotal", expression = "java(calcTotal(stock))"),

      // Атрибуты (в этом ответе обычно не приходят)
      @Mapping(target = "barcode", ignore = true),
      @Mapping(target = "category", ignore = true),
      @Mapping(target = "subject", ignore = true),
      @Mapping(target = "brand", ignore = true),
      @Mapping(target = "techSize", ignore = true),

      // Цена/скидка — Ozon в этом методе не отдаёт
      @Mapping(target = "priceOriginal", ignore = true),
      @Mapping(target = "discountPercent", ignore = true),

      // Время
      @Mapping(target = "lastChangeDate", ignore = true)
  })
  protected abstract StockDto toDto(OzonStockRaw.Item item, OzonStockRaw.Stock stock);

  /** Удобный flattener: из полного ответа делаем список StockDto (на каждый склад записи товара). */
  public List<StockDto> toDtos(OzonStockRaw raw) {
    List<StockDto> result = new ArrayList<>();
    if (raw == null || raw.result() == null || raw.result().items() == null) return result;
    for (var item : raw.result().items()) {
      if (item == null || item.stocks() == null) continue;
      for (var stock : item.stocks()) {
        result.add(toDto(item, stock));
      }
    }
    return result;
  }

  protected Integer calcTotal(OzonStockRaw.Stock s) {
    if (s == null) return null;
    int present = nz(s.present());
    int reserved = nz(s.reserved());
    int reservedPickup = nz(s.reserved_pickup()); // если поле есть в raw
    return present + reserved + reservedPickup;
  }

  protected int nz(Integer v) { return v == null ? 0 : v; }
  protected String toStringSafe(Long v) { return v == null ? null : v.toString(); }
  protected String toStringSafe(Object v) { return v == null ? null : String.valueOf(v); }
}
