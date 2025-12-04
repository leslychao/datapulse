package io.datapulse.etl.domain.policy;

import io.datapulse.etl.domain.entity.BackoffRequest;
import io.datapulse.etl.domain.entity.Execution;
import io.datapulse.etl.domain.entity.RetryDecision;

public interface RetryPolicy {

  RetryDecision decide(Execution execution, BackoffRequest request);
}
