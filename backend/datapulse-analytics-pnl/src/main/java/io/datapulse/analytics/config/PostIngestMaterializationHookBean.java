package io.datapulse.analytics.config;

import io.datapulse.analytics.domain.MaterializationService;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import io.datapulse.platform.etl.PostIngestMaterializationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostIngestMaterializationHookBean implements PostIngestMaterializationHook {

  private final MaterializationService materializationService;

  @Override
  public PostIngestMaterializationResult afterSuccessfulIngest(long jobExecutionId) {
    log.info("Running incremental mart materialization after ingest: jobExecutionId={}",
        jobExecutionId);
    return materializationService.runIncrementalMaterialization(jobExecutionId);
  }
}
