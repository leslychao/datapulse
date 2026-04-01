package io.datapulse.promotions.domain;

record RunCounters(int participateCount, int declineCount,
                    int pendingReviewCount, int deactivateCount,
                    PromoRunStatus status) {

    static final RunCounters EMPTY = new RunCounters(0, 0, 0, 0, PromoRunStatus.COMPLETED);
}
