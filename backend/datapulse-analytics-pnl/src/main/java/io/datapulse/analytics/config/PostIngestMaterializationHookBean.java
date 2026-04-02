package io.datapulse.analytics.config;

import io.datapulse.analytics.domain.MaterializationService;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostIngestMaterializationHookBean implements PostIngestMaterializationHook {

  private final MaterializationService materializationService;

  @Override
  public void afterSuccessfulIngest(long jobExecutionId) {
    log.info("Running incremental mart materialization after ingest: jobExecutionId={}",
        jobExecutionId);
    materializationService.runIncrementalMaterialization(jobExecutionId);
  }
}
