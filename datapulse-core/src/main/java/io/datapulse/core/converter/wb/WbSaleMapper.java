package io.datapulse.core.converter.wb;

import io.datapulse.core.converter.TimeMapper;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.domain.dto.raw.wb.WbSaleRaw;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = TimeMapper.class, imports = BigDecimal.class)
public interface WbSaleMapper {

  @Mappings({
      @Mapping(target = "sku", source = "nmId", qualifiedByName = "toStringSafe"),
      @Mapping(target = "postingNumber", source = "gNumber"),
      @Mapping(target = "offerId", source = "supplierArticle"),
      @Mapping(target = "fulfillment", ignore = true), // по WB sales не всегда однозначно
      @Mapping(target = "status", expression = "java(src.isRealization() != null && src.isRealization() ? \"sale\" : (src.isSupply()!=null && src.isSupply() ? \"supply\" : \"event\"))"),
      @Mapping(target = "isCancelled", ignore = true),
      @Mapping(target = "isReturn", expression = "java(src.saleID() != null ? src.saleID().startsWith(\"R\") : Boolean.FALSE)"),
      @Mapping(target = "eventDate", source = "date"),
      @Mapping(target = "createdAt", source = "lastChangeDate"),
      @Mapping(target = "processedAt", ignore = true),
      @Mapping(target = "quantity", constant = "1"),
      @Mapping(target = "priceOriginal", source = "totalPrice"),
      @Mapping(target = "priceFinal", source = "priceWithDisc"),
      @Mapping(target = "revenue", source = "forPay"),
      @Mapping(target = "cost", ignore = true),
      @Mapping(target = "commissionAmount", ignore = true),
      @Mapping(target = "deliveryAmount", ignore = true),
      @Mapping(target = "storageFeeAmount", ignore = true),
      @Mapping(target = "penaltyAmount", ignore = true),
      @Mapping(target = "marketingAmount", ignore = true)
  })
  SaleDto toDto(WbSaleRaw src);

  @Named("toStringSafe")
  default String toStringSafe(Long value) {
    return value == null ? null : value.toString();
  }
}
