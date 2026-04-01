package io.datapulse.promotions.domain;

public record PromoEvaluationCompletedEvent(
        long runId,
        long workspaceId,
        long connectionId,
        int participateCount,
        int declineCount,
        int pendingReviewCount,
        int deactivateCount,
        PromoRunStatus status
) {
}
