package io.datapulse.execution.domain.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.SimulatedOfferStateEntity;
import io.datapulse.execution.persistence.SimulatedOfferStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Simulated gateway: no real API call. Updates shadow-state and returns deterministic SUCCEEDED.
 * Per execution.md: simulated mode tests logic correctness, not error handling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatedPriceActionGateway implements PriceActionGateway {

    private final SimulatedOfferStateRepository simulatedStateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.SIMULATED;
    }

    @Override
    public GatewayResult execute(PriceActionEntity action, OfferExecutionContext context) {
        if (action.getExecutionMode() != ActionExecutionMode.SIMULATED) {
            throw new IllegalStateException(
                    "Simulated gateway received non-SIMULATED action: actionId=%d, mode=%s"
                            .formatted(action.getId(), action.getExecutionMode()));
        }

        BigDecimal targetPrice = action.getTargetPrice();
        BigDecimal canonicalPrice = action.getCurrentPriceAtCreation();

        var existingState = simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(
                context.workspaceId(), context.offerId());

        SimulatedOfferStateEntity state;
        if (existingState.isPresent()) {
            state = existingState.get();
            state.setPreviousSimulatedPrice(state.getSimulatedPrice());
        } else {
            state = new SimulatedOfferStateEntity();
            state.setWorkspaceId(context.workspaceId());
            state.setMarketplaceOfferId(context.offerId());
        }

        state.setSimulatedPrice(targetPrice);
        state.setSimulatedAt(OffsetDateTime.now());
        state.setPriceActionId(action.getId());
        state.setCanonicalPriceAtSimulation(canonicalPrice);

        BigDecimal delta = targetPrice.subtract(canonicalPrice);
        state.setPriceDelta(delta);

        if (canonicalPrice.compareTo(BigDecimal.ZERO) != 0) {
            state.setPriceDeltaPct(delta
                    .divide(canonicalPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        }

        simulatedStateRepository.save(state);

        String responseSummary = serialize(Map.of(
                "simulated", true,
                "targetPrice", targetPrice,
                "canonicalPrice", canonicalPrice,
                "delta", delta
        ));

        log.info("Simulated execution: actionId={}, offerId={}, targetPrice={}",
                action.getId(), context.offerId(), targetPrice);

        return GatewayResult.confirmed(null, responseSummary);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
