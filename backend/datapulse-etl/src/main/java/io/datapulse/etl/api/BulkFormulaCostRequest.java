package io.datapulse.etl.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.datapulse.etl.domain.CostUpdateOperation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BulkFormulaCostRequest(
    @NotEmpty @Size(max = 500) List<@NotNull Long> sellerSkuIds,
    @NotNull CostUpdateOperation operation,
    @NotNull @DecimalMin("0.01") BigDecimal value,
    @NotNull LocalDate validFrom
) {
}
