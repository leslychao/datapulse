package io.datapulse.analytics.domain;

public interface AnalyticsMaterializer {

    void materializeFull();

    void materializeIncremental(long jobExecutionId);

    String tableName();

    MaterializationPhase phase();
}
