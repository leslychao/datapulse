package io.datapulse.etl.application.resolver;

import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.policy.RetryPolicy;

public interface RetryPolicyResolver {

  RetryPolicy resolve(Event event);
}
