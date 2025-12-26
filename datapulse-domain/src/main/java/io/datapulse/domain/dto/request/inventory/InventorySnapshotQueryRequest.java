package io.datapulse.domain.dto.request.inventory;

import io.datapulse.domain.MarketplaceType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InventorySnapshotQueryRequest(

    @NotNull
    Long accountId,

    @NotNull
    MarketplaceType marketplace,

    LocalDate fromDate,
    LocalDate toDate,

    String sourceProductId,
    Long warehouseId
) {

}
