package io.datapulse.etl.application.resolver;

import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.MaterializationPlan;

public interface ExecutionPlanResolver {

  MaterializationPlan resolve(Event event);
}
