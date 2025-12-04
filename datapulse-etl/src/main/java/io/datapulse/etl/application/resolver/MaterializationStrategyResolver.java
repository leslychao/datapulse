package io.datapulse.etl.application.resolver;

import io.datapulse.etl.domain.entity.MaterializationPlan;
import io.datapulse.etl.domain.entity.Event;

public interface MaterializationStrategyResolver {

  MaterializationStrategy resolve(MaterializationPlan plan);

  interface MaterializationStrategy {

    void execute(Event event, MaterializationPlan plan);
  }
}
