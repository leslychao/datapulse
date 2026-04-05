package io.datapulse.execution.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@Getter
@RequiredArgsConstructor
@ConfigurationProperties("datapulse.execution")
public class ExecutionProperties {

    private final int maxAttempts;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final int backoffMultiplier;
    private final int approvalTimeoutHours;
    private final Duration readAfterWriteDelay;
    private final Reconciliation reconciliation;
    private final StuckState stuckState;

    public Duration getReadAfterWriteDelay() {
        return readAfterWriteDelay != null ? readAfterWriteDelay : Duration.ofSeconds(2);
    }

    public int getMaxAttempts() {
        return maxAttempts > 0 ? maxAttempts : 3;
    }

    public Duration getMinBackoff() {
        return minBackoff != null ? minBackoff : Duration.ofSeconds(5);
    }

    public Duration getMaxBackoff() {
        return maxBackoff != null ? maxBackoff : Duration.ofMinutes(5);
    }

    public int getBackoffMultiplier() {
        return backoffMultiplier > 0 ? backoffMultiplier : 2;
    }

    public int getApprovalTimeoutHours() {
        return approvalTimeoutHours > 0 ? approvalTimeoutHours : 24;
    }

    @Validated
    @Getter
    @RequiredArgsConstructor
    public static class Reconciliation {

        private final Duration initialDelay;
        private final int backoffMultiplier;
        private final int maxAttempts;
        private final Duration timeout;

        public Duration getInitialDelay() {
            return initialDelay != null ? initialDelay : Duration.ofSeconds(30);
        }

        public int getBackoffMultiplier() {
            return backoffMultiplier > 0 ? backoffMultiplier : 2;
        }

        public int getMaxAttempts() {
            return maxAttempts > 0 ? maxAttempts : 3;
        }

        public Duration getTimeout() {
            return timeout != null ? timeout : Duration.ofMinutes(10);
        }
    }

    @Validated
    @Getter
    @RequiredArgsConstructor
    public static class StuckState {

        private final Duration interval;
        private final Duration executingTtl;
        private final Duration retryScheduledGrace;
        private final Duration reconciliationPendingTtl;
        private final Duration scheduledTtl;

        public Duration getInterval() {
            return interval != null ? interval : Duration.ofMinutes(5);
        }

        public Duration getExecutingTtl() {
            return executingTtl != null ? executingTtl : Duration.ofMinutes(5);
        }

        public Duration getRetryScheduledGrace() {
            return retryScheduledGrace != null ? retryScheduledGrace : Duration.ofMinutes(5);
        }

        public Duration getReconciliationPendingTtl() {
            return reconciliationPendingTtl != null ? reconciliationPendingTtl : Duration.ofMinutes(10);
        }

        public Duration getScheduledTtl() {
            return scheduledTtl != null ? scheduledTtl : Duration.ofMinutes(5);
        }
    }
}
