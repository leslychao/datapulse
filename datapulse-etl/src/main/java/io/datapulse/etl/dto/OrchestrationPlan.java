package io.datapulse.etl.dto;

import java.util.List;

public record OrchestrationPlan(
    List<MarketplacePlan> plans,
    int expectedExecutions
) {

}
