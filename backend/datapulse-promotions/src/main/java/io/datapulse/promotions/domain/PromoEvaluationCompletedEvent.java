package io.datapulse.promotions.domain;

/**
 * Published when a promo evaluation run reaches a terminal state.
 * No listeners yet — intended for alert on FAILED and STOMP push.
 */
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
