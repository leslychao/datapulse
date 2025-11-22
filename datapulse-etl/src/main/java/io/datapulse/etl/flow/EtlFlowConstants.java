package io.datapulse.etl.flow;

public final class EtlFlowConstants {

  private EtlFlowConstants() {
  }

  public static final String CH_ETL_ORCHESTRATE = "ETL_ORCHESTRATE";
  public static final String CH_ETL_INGEST = "ETL_INGEST";
  public static final String CH_ETL_SAVE_BATCH = "ETL_SAVE_BATCH";
  public static final String CH_ETL_EVENT_VIEWS_BUILT = "ETL_EVENT_VIEWS_BUILT";
  public static final String CH_ETL_SNAPSHOT_READY = "ETL_SNAPSHOT_READY";
  public static final String CH_ETL_RUN_CORE = "chEtlRunCore";

  public static final String HDR_ETL_REQUEST_ID = "ETL_REQUEST_ID";
  public static final String HDR_ETL_ACCOUNT_ID = "ETL_ACCOUNT_ID";
  public static final String HDR_ETL_EVENT = "ETL_EVENT";
  public static final String HDR_ETL_DATE_FROM = "ETL_DATE_FROM";
  public static final String HDR_ETL_DATE_TO = "ETL_DATE_TO";
  public static final String HDR_ETL_SOURCE_MP = "ETL_SOURCE_MARKETPLACE";
  public static final String HDR_ETL_SOURCE_ID = "ETL_SOURCE_ID";
  public static final String HDR_ETL_SNAPSHOT_FILE = "ETL_SNAPSHOT_FILE";
  public static final String HDR_ETL_SNAPSHOT_ID = "ETL_SNAPSHOT_ID";
  public static final String HDR_ETL_TOTAL_EXECUTIONS = "ETL_TOTAL_EXECUTIONS";
  public static final String HDR_ETL_SYNC_STATUS = "ETL_SYNC_STATUS";
  public static final String HDR_ETL_FAILED_SOURCE_IDS = "ETL_FAILED_SOURCE_IDS";
  public static final String HDR_ETL_ERROR_MESSAGE = "ETL_ERROR_MESSAGE";

}
