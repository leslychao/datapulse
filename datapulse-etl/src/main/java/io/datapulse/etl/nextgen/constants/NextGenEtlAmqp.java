package io.datapulse.etl.nextgen.constants;

public final class NextGenEtlAmqp {

  public static final String EXCHANGE_EXECUTION = "etl.execution.ng";
  public static final String EXCHANGE_EXECUTION_DLX = "etl.execution.ng.dlx";
  public static final String QUEUE_EXECUTION = "etl.execution.ng.queue";
  public static final String QUEUE_EXECUTION_WAIT = "etl.execution.ng.wait";
  public static final String QUEUE_INGEST = "etl.ingest.ng.queue";
  public static final String QUEUE_NORMALIZE = "etl.normalize.ng.queue";
  public static final String QUEUE_MATERIALIZE = "etl.materialize.ng.queue";
  public static final String ROUTING_EXECUTION = "etl.execution.ng";
  public static final String ROUTING_EXECUTION_WAIT = "etl.execution.ng.wait";
  public static final String ROUTING_INGEST = "etl.ingest.ng";
  public static final String ROUTING_NORMALIZE = "etl.normalize.ng";
  public static final String ROUTING_MATERIALIZE = "etl.materialize.ng";

  private NextGenEtlAmqp() {
  }
}
