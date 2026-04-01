package io.datapulse.etl.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateCostProfileRequest(
    @NotNull @DecimalMin("0.01") BigDecimal costPrice,
    @NotBlank String currency,
    @NotNull LocalDate validFrom
) {
}
