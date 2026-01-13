package io.datapulse.etl.dto;

import io.datapulse.domain.MarketplaceType;
import java.util.List;

public record MarketplacePlan(
    MarketplaceType marketplace,
    List<EtlSourceExecution> executions
) {

}
