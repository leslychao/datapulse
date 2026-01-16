package io.datapulse.core.entity.product;

import io.datapulse.core.entity.LongBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "dim_product")
public class DimProductEntity extends LongBaseEntity {

  private Long accountId;

  private String sourcePlatform;
  private String sourceProductId;

  private String offerId;

  private Long externalCategoryId;
}
