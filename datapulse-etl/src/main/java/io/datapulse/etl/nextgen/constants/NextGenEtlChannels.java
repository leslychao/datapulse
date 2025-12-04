package io.datapulse.etl.nextgen.constants;

public final class NextGenEtlChannels {

  public static final String CH_ORCHESTRATE = "ngEtlOrchestrate";
  public static final String CH_NGES_REQUEST = "ngEtlNgesRequest";
  public static final String CH_EXECUTION = "ngEtlExecution";
  public static final String CH_INGEST = "ngEtlIngest";
  public static final String CH_NORMALIZE = "ngEtlNormalize";
  public static final String CH_MATERIALIZE = "ngEtlMaterialize";
  public static final String CH_MATERIALIZE_GATE = "ngEtlMaterializeGate";
  public static final String CH_AUDIT = "ngEtlAudit";

  private NextGenEtlChannels() {
  }
}
