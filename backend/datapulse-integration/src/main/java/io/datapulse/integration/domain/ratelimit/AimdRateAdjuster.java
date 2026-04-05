package io.datapulse.integration.domain.ratelimit;

import io.datapulse.integration.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AIMD (Additive Increase, Multiplicative Decrease) adaptive rate controller.
 * Tracks per (connectionId, group) state. State is not persisted —
 * on restart, each group starts from its initial_rate (conservative start).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AimdRateAdjuster {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, AimdState> states = new ConcurrentHashMap<>();

    public double getCurrentRate(long connectionId, RateLimitGroup group) {
        String key = buildKey(connectionId, group);
        AimdState state = states.get(key);
        return state != null ? state.currentRate : resolveInitialRate(group);
    }

    public void onThrottle(long connectionId, RateLimitGroup group) {
        String key = buildKey(connectionId, group);
        states.compute(key, (k, state) -> {
            if (state == null) {
                state = new AimdState(resolveInitialRate(group));
            }
            double minRate = resolveMinRate(group);
            state.currentRate = Math.max(state.currentRate * properties.getDecreaseFactor(), minRate);
            state.consecutiveSuccesses = 0;
            log.info("AIMD throttle: connectionId={}, group={}, newRate={}",
                    connectionId, group, state.currentRate);
            return state;
        });
    }

    public void onSuccess(long connectionId, RateLimitGroup group) {
        String key = buildKey(connectionId, group);
        states.compute(key, (k, state) -> {
            if (state == null) {
                state = new AimdState(resolveInitialRate(group));
            }
            state.consecutiveSuccesses++;
            if (state.consecutiveSuccesses >= properties.getStabilityWindow()) {
                double maxRate = resolveMaxRate(group);
                double newRate = Math.min(state.currentRate * (1 + properties.getIncreasePct()), maxRate);
                if (newRate > state.currentRate) {
                    log.debug("AIMD increase: connectionId={}, group={}, oldRate={}, newRate={}",
                            connectionId, group, state.currentRate, newRate);
                    state.currentRate = newRate;
                }
                state.consecutiveSuccesses = 0;
            }
            return state;
        });
    }

    private double resolveInitialRate(RateLimitGroup group) {
        RateLimitProperties.GroupOverride override = properties.getGroups().get(group.name());
        if (override != null && override.getInitialRate() != null) {
            return override.getInitialRate();
        }
        return group.getInitialRate();
    }

    private double resolveMinRate(RateLimitGroup group) {
        RateLimitProperties.GroupOverride override = properties.getGroups().get(group.name());
        if (override != null && override.getMinRate() != null) {
            return override.getMinRate();
        }
        return properties.getMinRate();
    }

    private double resolveMaxRate(RateLimitGroup group) {
        RateLimitProperties.GroupOverride override = properties.getGroups().get(group.name());
        if (override != null && override.getMaxRate() != null) {
            return override.getMaxRate();
        }
        return resolveInitialRate(group) * 2.0;
    }

    int resolveBurst(RateLimitGroup group) {
        RateLimitProperties.GroupOverride override = properties.getGroups().get(group.name());
        if (override != null && override.getBurst() != null) {
            return override.getBurst();
        }
        return group.getBurst();
    }

    private String buildKey(long connectionId, RateLimitGroup group) {
        return connectionId + ":" + group.name();
    }

    private static class AimdState {
        double currentRate;
        int consecutiveSuccesses;

        AimdState(double initialRate) {
            this.currentRate = initialRate;
            this.consecutiveSuccesses = 0;
        }
    }
}
