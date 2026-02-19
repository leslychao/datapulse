package io.datapulse.etl.v1.dto;

import java.util.List;

public record OrchestrationPlan(
    List<MarketplacePlan> plans,
    int expectedExecutions
) {

}
