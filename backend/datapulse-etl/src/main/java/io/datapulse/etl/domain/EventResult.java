package io.datapulse.etl.domain;

import java.util.List;

public record EventResult(
        EtlEventType eventType,
        EventResultStatus status,
        String lastCursor,
        String skipReason,
        List<SubSourceResult> subSourceResults
) {

    public boolean isSuccess() {
        return status == EventResultStatus.COMPLETED || status == EventResultStatus.COMPLETED_WITH_ERRORS;
    }

    public boolean isFailed() {
        return status == EventResultStatus.FAILED;
    }

    public boolean isSkipped() {
        return status == EventResultStatus.SKIPPED;
    }

    public static EventResult completed(EtlEventType eventType, List<SubSourceResult> subSources) {
        return new EventResult(eventType, EventResultStatus.COMPLETED, null, null, subSources);
    }

    public static EventResult completedWithErrors(EtlEventType eventType,
                                                   List<SubSourceResult> subSources) {
        return new EventResult(eventType, EventResultStatus.COMPLETED_WITH_ERRORS, null, null, subSources);
    }

    public static EventResult failed(EtlEventType eventType, List<SubSourceResult> subSources) {
        String cursor = subSources.stream()
                .filter(s -> s.lastCursor() != null)
                .reduce((first, second) -> second)
                .map(SubSourceResult::lastCursor)
                .orElse(null);
        return new EventResult(eventType, EventResultStatus.FAILED, cursor, null, subSources);
    }

    public static EventResult skipped(EtlEventType eventType, String reason) {
        return new EventResult(eventType, EventResultStatus.SKIPPED, null, reason, List.of());
    }

    /**
     * Derives the overall status from individual sub-source results.
     */
    public static EventResult fromSubSources(EtlEventType eventType, List<SubSourceResult> subSources) {
        boolean anyFailed = subSources.stream().anyMatch(s -> s.status() == EventResultStatus.FAILED);
        boolean anyPartial = subSources.stream().anyMatch(s -> s.status() == EventResultStatus.COMPLETED_WITH_ERRORS);

        if (anyFailed) {
            boolean allFailed = subSources.stream().allMatch(s -> s.status() == EventResultStatus.FAILED);
            return allFailed ? failed(eventType, subSources) : completedWithErrors(eventType, subSources);
        }
        if (anyPartial) {
            return completedWithErrors(eventType, subSources);
        }
        return completed(eventType, subSources);
    }
}
