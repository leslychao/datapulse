package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
public class ProductEntity extends LongBaseEntity {

  private Long accountId;
  private String sku;
  private String name;
  private String brand;
  private String category;
  private String barcode;
}