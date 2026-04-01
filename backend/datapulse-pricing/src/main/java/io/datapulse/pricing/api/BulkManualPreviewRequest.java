package io.datapulse.pricing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record BulkManualPreviewRequest(
        @NotEmpty @Size(max = 500) @Valid List<PriceChange> changes
) {

    public record PriceChange(
            @NotNull Long marketplaceOfferId,
            @NotNull @DecimalMin("0.01") BigDecimal targetPrice
    ) {
    }
}
