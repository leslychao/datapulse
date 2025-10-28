package io.datapulse.marketplaces.mapper.wb;

import io.datapulse.domain.dto.StockDto;
import io.datapulse.marketplaces.dto.raw.wb.WbStockRaw;
import org.mapstruct.*;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", imports = BigDecimal.class)
public interface WbStockMapper {

  @Mappings({
      // Идентификаторы
      @Mapping(target = "sku", expression = "java(toStringSafe(src.nmId()))"),
      @Mapping(target = "offerId", source = "supplierArticle"),
      @Mapping(target = "productId", expression = "java(toStringSafe(src.nmId()))"),

      // Склад
      @Mapping(target = "warehouseId", source = "scCode"), // SCCode — ближайший аналог id
      @Mapping(target = "warehouseName", source = "warehouseName"),

      // Остатки
      @Mapping(target = "quantityAvailable", source = "quantity"),
      @Mapping(target = "quantityReserved", ignore = true), // у WB stocks нет прямого reserved
      @Mapping(target = "quantityInTransitToClient", source = "inWayToClient"),
      @Mapping(target = "quantityInTransitFromClient", source = "inWayFromClient"),
      @Mapping(target = "quantityTotal", source = "quantityFull"),

      // Атрибуты
      @Mapping(target = "barcode", source = "barcode"),
      @Mapping(target = "category", source = "category"),
      @Mapping(target = "subject", source = "subject"),
      @Mapping(target = "brand", source = "brand"),
      @Mapping(target = "techSize", source = "techSize"),

      // Цена/скидка
      @Mapping(target = "priceOriginal", source = "price"),
      @Mapping(target = "discountPercent", source = "discount"),

      // Время
      @Mapping(target = "lastChangeDate", source = "lastChangeDate")
  })
  StockDto toDto(WbStockRaw src);

  default String toStringSafe(Long v) { return v == null ? null : v.toString(); }
}
