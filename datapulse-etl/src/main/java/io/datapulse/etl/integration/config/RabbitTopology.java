package io.datapulse.etl.integration.config;

public final class RabbitTopology {

  public static final String EXECUTION_WORKER_QUEUE = "etl.execution.worker";
  public static final String RETRY_WAIT_QUEUE = "etl.execution.retry.wait";
  public static final String EVENT_ORCHESTRATOR_QUEUE = "etl.event.orchestrator";
  public static final String MATERIALIZATION_QUEUE = "etl.materialization";
  public static final String AUDIT_QUEUE = "etl.audit";

  private RabbitTopology() {
  }
}
