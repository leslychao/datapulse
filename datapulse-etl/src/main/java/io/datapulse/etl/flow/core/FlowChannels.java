package io.datapulse.etl.flow.core;

/**
 * Centralized registry of channel names used across the ETL integration flows.
 * Each entry is consumed via gateway boundaries to keep the immutable core stable.
 */
public final class FlowChannels {

  private FlowChannels() {
  }

  public static final String CH_ORCHESTRATE = "ETL_ORCHESTRATE";
  public static final String CH_EXECUTION_INBOUND = "ETL_EXECUTION_INBOUND";
  public static final String CH_EXECUTION_CORE = "ETL_EXECUTION_CORE";
  public static final String CH_EXECUTION_RESULT = "ETL_EXECUTION_RESULT";
  public static final String CH_SNAPSHOT_READY = "ETL_SNAPSHOT_READY";
  public static final String CH_MATERIALIZATION_GATE = "ETL_MATERIALIZATION_GATE";
  public static final String CH_EVENT_AUDIT = "ETL_EVENT_AUDIT";
  public static final String CH_ERROR = "ETL_ERROR";
}
