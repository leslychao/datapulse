package io.datapulse.etl.flow.core;

public final class EtlFlowConstants {

  private EtlFlowConstants() {
  }

  public static final String CH_ETL_ORCHESTRATE = "ETL_ORCHESTRATE";
  public static final String CH_ETL_INGEST = "ETL_INGEST";
  public static final String CH_ETL_RUN_CORE = "ETL_RUN_CORE";
  public static final String CH_ETL_ORCHESTRATION_RESULT = "ETL_ORCHESTRATION_RESULT";

  public static final String HDR_ETL_EXECUTION_GROUP_ID = "ETL_EXECUTION_GROUP_ID";
}
