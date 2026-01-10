package io.datapulse.domain.dto.request.orderpnl;

import io.datapulse.domain.MarketplaceType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record OrderPnlQueryRequest(

    @NotNull
    Long accountId,

    MarketplaceType sourcePlatform,

    LocalDate dateFrom,

    LocalDate dateTo,

    Boolean isReturned,

    Boolean hasPenalties
) {

}
