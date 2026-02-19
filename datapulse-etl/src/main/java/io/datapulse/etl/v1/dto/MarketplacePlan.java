package io.datapulse.etl.v1.dto;

import io.datapulse.domain.MarketplaceType;
import java.util.List;

public record MarketplacePlan(
    MarketplaceType marketplace,
    List<EtlSourceExecution> executions
) {

}
