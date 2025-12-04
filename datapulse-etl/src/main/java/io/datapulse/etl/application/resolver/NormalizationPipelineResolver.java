package io.datapulse.etl.application.resolver;

import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.IngestionResult;

public interface NormalizationPipelineResolver {

  NormalizationPipeline resolve(Event event);

  interface NormalizationPipeline {

    IngestionResult execute(Event event);
  }
}
