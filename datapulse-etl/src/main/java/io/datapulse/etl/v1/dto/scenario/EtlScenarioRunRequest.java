package io.datapulse.etl.v1.dto.scenario;

import java.util.List;

public record EtlScenarioRunRequest(
    long accountId,
    List<EtlScenarioEventConfig> events
) {

}
