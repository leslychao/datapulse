package io.datapulse.etl.v1.flow.core;

public final class EtlExecutionAmqpConstants {

  private EtlExecutionAmqpConstants() {
  }

  public static final String EXCHANGE_TASKS = "etl.tasks.exchange";
  public static final String EXCHANGE_EXECUTION = "etl.execution.exchange";
  public static final String EXCHANGE_EXECUTION_DLX = "etl.execution.exchange.dlx";

  public static final String QUEUE_TASKS = "etl.tasks.queue";
  public static final String QUEUE_EXECUTION = "etl.execution.queue";
  public static final String QUEUE_EXECUTION_WAIT = "etl.execution.wait.queue";

  public static final String ROUTING_KEY_TASKS = "task.run";
  public static final String ROUTING_KEY_EXECUTION = "execution.run";
  public static final String ROUTING_KEY_EXECUTION_WAIT = "execution.wait";
}
