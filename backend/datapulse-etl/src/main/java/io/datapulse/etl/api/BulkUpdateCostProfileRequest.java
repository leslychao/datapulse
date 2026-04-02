package io.datapulse.etl.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BulkUpdateCostProfileRequest(
    @NotEmpty @Size(max = 1000) @Valid List<Item> items
) {

  public record Item(
      @NotNull Long sellerSkuId,
      @NotNull @DecimalMin("0.01") BigDecimal costPrice,
      @NotBlank String currency,
      @NotNull LocalDate validFrom
  ) {
  }
}
