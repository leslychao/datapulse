package io.datapulse.analytics.domain;

public interface AnalyticsMaterializer {

    void materializeFull();

    void materializeIncremental(long jobExecutionId);

    String tableName();

    MaterializationPhase phase();

    /**
     * Execution order within the same phase. Lower values run first.
     * Override when a materializer depends on another materializer's output
     * within the same phase (e.g. MartProductPnl depends on MartPostingPnl).
     */
    default int order() {
        return 0;
    }
}
