package io.datapulse.execution.domain;

public enum AttemptOutcome {

    SUCCESS,
    RETRIABLE_FAILURE,
    NON_RETRIABLE_FAILURE,
    UNCERTAIN
}
