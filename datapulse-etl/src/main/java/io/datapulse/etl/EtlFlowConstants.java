package io.datapulse.etl;

public final class EtlFlowConstants {

  private EtlFlowConstants() {
  }

  public static final String CH_ETL_INGEST = "ETL_INGEST";
  public static final String CH_ETL_RUN_CORE = "ETL_RUN_CORE";
  public static final String CH_ETL_ORCHESTRATION_RESULT = "ETL_ORCHESTRATION_RESULT";
  public static final String CH_ETL_PREPARE_RAW_SCHEMA = "ETL_PREPARE_RAW_SCHEMA";
  public static final String CH_ETL_EVENT_COMPLETED = "ETL_EVENT_COMPLETED";
  public static final String CH_ETL_SCENARIO_RUN = "ETL_SCENARIO_RUN";
  public static final String CH_ETL_SCENARIO_STEPS = "ETL_SCENARIO_STEPS";

  public static final String HDR_ETL_SCENARIO_REQUEST_ID = "ETL_SCENARIO_REQUEST_ID";
  public static final String HDR_ETL_EXECUTION_GROUP_ID = "ETL_EXECUTION_GROUP_ID";
  public static final String HDR_ETL_EXPECTED_EXECUTIONS = "ETL_EXPECTED_EXECUTIONS";
}
