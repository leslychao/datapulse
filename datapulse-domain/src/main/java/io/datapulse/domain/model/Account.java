package io.datapulse.domain.model;

import io.datapulse.domain.MarketplaceType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Account {
  private Long id;
  private MarketplaceType marketplace;
  private String name;
  private String tokenMasked;
  private boolean active;
}
