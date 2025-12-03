package io.datapulse.etl.flow.core;

public final class EtlFlowConstants {

  private EtlFlowConstants() {
  }

  public static final String CH_ETL_ORCHESTRATE = "ETL_ORCHESTRATE";
  public static final String CH_ETL_INGEST = "ETL_INGEST";
  public static final String CH_ETL_INGEST_CORE = "ETL_INGEST_CORE";
  public static final String CH_ETL_SNAPSHOT_READY = "ETL_SNAPSHOT_READY";
  public static final String CH_ETL_RUN_CORE = "ETL_RUN_CORE";
  public static final String CH_ETL_ORCHESTRATION_RESULT = "ETL_ORCHESTRATION_RESULT";

  public static final String HDR_ETL_REQUEST_ID = "ETL_REQUEST_ID";
  public static final String HDR_ETL_ACCOUNT_ID = "ETL_ACCOUNT_ID";
  public static final String HDR_ETL_EVENT = "ETL_EVENT";
  public static final String HDR_ETL_DATE_FROM = "ETL_DATE_FROM";
  public static final String HDR_ETL_DATE_TO = "ETL_DATE_TO";
  public static final String HDR_ETL_SOURCE_MARKETPLACE = "ETL_SOURCE_MARKETPLACE";
  public static final String HDR_ETL_SOURCE_ID = "ETL_SOURCE_ID";
  public static final String HDR_ETL_SNAPSHOT_FILE = "ETL_SNAPSHOT_FILE";
  public static final String HDR_ETL_FETCHED_DATA = "ETL_FETCHED_DATA";
  public static final String HDR_ETL_PROCESS_ID = "ETL_PROCESS_ID";
  public static final String HDR_ETL_EXPECTED_SOURCE_IDS = "ETL_EXPECTED_SOURCE_IDS";
  public static final String HDR_ETL_SYNC_STATUS = "ETL_SYNC_STATUS";
  public static final String HDR_ETL_ERROR_MESSAGE = "ETL_ERROR_MESSAGE";
  public static final String HDR_ETL_RAW_TABLE = "ETL_RAW_TABLE";
  public static final String HDR_RETRY_AFTER = "X-RETRY-AFTER";
}
