package io.datapulse.execution.domain.gateway;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.persistence.PriceActionEntity;

/**
 * Strategy interface for price action execution.
 * Two implementations selected by execution_mode:
 * - LIVE → LivePriceActionGateway (real marketplace API)
 * - SIMULATED → SimulatedPriceActionGateway (shadow-state update)
 */
public interface PriceActionGateway {

    ActionExecutionMode executionMode();

    GatewayResult execute(PriceActionEntity action, OfferExecutionContext context);
}
