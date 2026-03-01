package io.datapulse.etl;

public final class EtlTasksAmqpConstants {

  private EtlTasksAmqpConstants() {
  }

  public static final String EXCHANGE_TASKS = "etl.tasks.exchange";
  public static final String QUEUE_TASKS = "etl.tasks.commands";
  public static final String ROUTING_KEY_TASKS = "tasks.command";
}
