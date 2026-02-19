package io.datapulse.etl.v1.flow.core;

public class EtlExecutionAmqpConstants {

  private EtlExecutionAmqpConstants() {
  }

  public static final String EXCHANGE_EXECUTION = "etl.execution.exchange";
  public static final String EXCHANGE_EXECUTION_DLX = "etl.execution.exchange.dlx";
  public static final String QUEUE_EXECUTION = "etl.execution.commands";
  public static final String QUEUE_EXECUTION_WAIT = "etl.execution.commands.wait";
  public static final String ROUTING_KEY_EXECUTION = "execution.command";
  public static final String ROUTING_KEY_EXECUTION_WAIT = "execution.command.wait";

}
