package io.datapulse.domain.model;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Sale {

  Long accountId;
  String sku;
  LocalDate date;
  int quantity;
  double revenue;
  double cost;
  double margin;
}
