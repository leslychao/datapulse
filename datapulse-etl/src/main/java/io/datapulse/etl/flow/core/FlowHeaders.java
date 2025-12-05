package io.datapulse.etl.flow.core;

public final class FlowHeaders {

  private FlowHeaders() {
  }

  public static final String HDR_REQUEST_ID = "ETL_REQUEST_ID";
  public static final String HDR_ACCOUNT_ID = "ETL_ACCOUNT_ID";
  public static final String HDR_EVENT = "ETL_EVENT";
  public static final String HDR_DATE_FROM = "ETL_DATE_FROM";
  public static final String HDR_DATE_TO = "ETL_DATE_TO";
  public static final String HDR_MARKETPLACE = "ETL_MARKETPLACE";
  public static final String HDR_SOURCE_ID = "ETL_SOURCE_ID";
  public static final String HDR_RAW_TABLE = "ETL_RAW_TABLE";
  public static final String HDR_EXPECTED_SOURCE_IDS = "ETL_EXPECTED_SOURCE_IDS";
  public static final String HDR_EXECUTION_STATUS = "ETL_EXECUTION_STATUS";
  public static final String HDR_ERROR_MESSAGE = "ETL_ERROR_MESSAGE";
  public static final String HDR_RETRY_AFTER = "ETL_RETRY_AFTER";
  public static final String HDR_EXECUTION_ID = "ETL_EXECUTION_ID";
  public static final String HDR_SNAPSHOT_ID = "ETL_SNAPSHOT_ID";
  public static final String HDR_EVENT_AGGREGATION = "ETL_EVENT_AGGREGATION";
}
