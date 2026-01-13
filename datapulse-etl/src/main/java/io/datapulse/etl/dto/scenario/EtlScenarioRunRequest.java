package io.datapulse.etl.dto.scenario;

import java.util.List;

public record EtlScenarioRunRequest(
    long accountId,
    List<EtlScenarioEventConfig> events
) {

}
