package io.datapulse.etl;

public final class EtlFlowHeaders {

  private EtlFlowHeaders() {
  }

  public static final String HDR_OUTCOME = "etlOutcome";
  public static final String HDR_TTL_MILLIS = "ttlMillis";

  public static final String HDR_WORK = "etlWork";
  public static final String HDR_RAW_TABLE = "rawTable";
  public static final String HDR_MARKETPLACE = "marketplace";

  public static final String HDR_REQUEST_ID = "requestId";
  public static final String HDR_EVENT = "event";
  public static final String HDR_SOURCE_ID = "sourceId";
}