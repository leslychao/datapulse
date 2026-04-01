package io.datapulse.execution.domain;

/**
 * Reconciliation source on the action level (AUTO vs MANUAL).
 * Distinct from attempt-level {@link ReconciliationSource} which tracks
 * how reconciliation evidence was obtained (IMMEDIATE / DEFERRED / MANUAL).
 */
public enum ActionReconciliationSource {

    AUTO,
    MANUAL
}
