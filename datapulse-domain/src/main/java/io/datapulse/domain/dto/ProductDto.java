package io.datapulse.domain.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ProductDto extends LongBaseDto {
  private Long accountId;
  private String sku;
  private String name;
  private String brand;
  private String category;
  private String barcode;
}