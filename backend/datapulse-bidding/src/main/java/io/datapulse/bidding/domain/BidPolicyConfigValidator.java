package io.datapulse.bidding.domain;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.bidding.domain.strategy.config.EconomyHoldConfig;
import io.datapulse.bidding.domain.strategy.config.GrowthConfig;
import io.datapulse.bidding.domain.strategy.config.LaunchConfig;
import io.datapulse.bidding.domain.strategy.config.LiquidationConfig;
import io.datapulse.bidding.domain.strategy.config.MinimalPresenceConfig;
import io.datapulse.bidding.domain.strategy.config.PositionHoldConfig;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BidPolicyConfigValidator {

  private static final Map<BiddingStrategyType, Class<?>> CONFIG_TYPES = Map.of(
      BiddingStrategyType.ECONOMY_HOLD, EconomyHoldConfig.class,
      BiddingStrategyType.GROWTH, GrowthConfig.class,
      BiddingStrategyType.LAUNCH, LaunchConfig.class,
      BiddingStrategyType.LIQUIDATION, LiquidationConfig.class,
      BiddingStrategyType.POSITION_HOLD, PositionHoldConfig.class,
      BiddingStrategyType.MINIMAL_PRESENCE, MinimalPresenceConfig.class
  );

  private final ObjectMapper objectMapper;
  private final Validator validator;

  public void validate(BiddingStrategyType strategyType, JsonNode config) {
    Class<?> configClass = CONFIG_TYPES.get(strategyType);
    if (configClass == null) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_POLICY_CONFIG_INVALID,
          "Unknown strategy type: " + strategyType);
    }

    Object parsed;
    try {
      parsed = objectMapper.treeToValue(config, configClass);
    } catch (Exception e) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_POLICY_CONFIG_INVALID,
          e.getMessage());
    }

    Set<ConstraintViolation<Object>> violations = validator.validate(parsed);
    if (!violations.isEmpty()) {
      String details = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .sorted()
          .collect(Collectors.joining("; "));
      throw BadRequestException.of(
          MessageCodes.BIDDING_POLICY_CONFIG_INVALID,
          details);
    }

    if (strategyType == BiddingStrategyType.LAUNCH) {
      validateLaunchTargetStrategy((LaunchConfig) parsed);
    }
  }

  private void validateLaunchTargetStrategy(LaunchConfig config) {
    try {
      BiddingStrategyType target = BiddingStrategyType.valueOf(
          config.targetStrategy());
      if (target == BiddingStrategyType.LAUNCH) {
        throw BadRequestException.of(
            MessageCodes.BIDDING_POLICY_CONFIG_INVALID,
            "targetStrategy cannot be LAUNCH (circular)");
      }
    } catch (IllegalArgumentException e) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_POLICY_CONFIG_INVALID,
          "Invalid targetStrategy: " + config.targetStrategy());
    }
  }
}
