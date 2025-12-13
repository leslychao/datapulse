package io.datapulse.etl.flow.core;

public final class EtlFlowConstants {

  private EtlFlowConstants() {
  }

  public static final String CH_ETL_INGEST = "ETL_INGEST";
  public static final String CH_ETL_RUN_CORE = "ETL_RUN_CORE";
  public static final String CH_ETL_ORCHESTRATION_RESULT = "ETL_ORCHESTRATION_RESULT";
  public static final String CH_ETL_PREPARE_RAW_SCHEMA = "chEtlPrepareRawSchema";

  public static final String HDR_ETL_EXECUTION_GROUP_ID = "ETL_EXECUTION_GROUP_ID";
  public static final String HDR_ETL_EXPECTED_EXECUTIONS = "ETL_EXPECTED_EXECUTIONS";
}
