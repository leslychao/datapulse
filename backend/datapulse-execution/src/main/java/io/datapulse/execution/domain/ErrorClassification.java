package io.datapulse.execution.domain;

public enum ErrorClassification {

    RETRIABLE_RATE_LIMIT,
    RETRIABLE_TRANSIENT,
    UNCERTAIN_TIMEOUT,
    NON_RETRIABLE,
    PROVIDER_ERROR
}
