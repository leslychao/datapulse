package io.datapulse.pricing.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.PolicyType;

class PricingStrategyRegistryTest {

  @Nested
  @DisplayName("resolve")
  class Resolve {

    @Test
    @DisplayName("returns correct strategy by type")
    void should_returnStrategy_when_typeRegistered() {
      PricingStrategy targetMargin = mockStrategy(PolicyType.TARGET_MARGIN);
      PricingStrategy corridor = mockStrategy(PolicyType.PRICE_CORRIDOR);

      PricingStrategyRegistry registry = new PricingStrategyRegistry(
          List.of(targetMargin, corridor));

      assertThat(registry.resolve(PolicyType.TARGET_MARGIN)).isSameAs(targetMargin);
      assertThat(registry.resolve(PolicyType.PRICE_CORRIDOR)).isSameAs(corridor);
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unregistered type")
    void should_throwIllegalArgument_when_typeNotRegistered() {
      PricingStrategy targetMargin = mockStrategy(PolicyType.TARGET_MARGIN);

      PricingStrategyRegistry registry = new PricingStrategyRegistry(
          List.of(targetMargin));

      assertThatThrownBy(() -> registry.resolve(PolicyType.PRICE_CORRIDOR))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No PricingStrategy registered for type");
    }
  }

  @Nested
  @DisplayName("constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("registers all provided strategies")
    void should_registerAll_when_multipleStrategies() {
      PricingStrategy s1 = mockStrategy(PolicyType.TARGET_MARGIN);
      PricingStrategy s2 = mockStrategy(PolicyType.PRICE_CORRIDOR);

      PricingStrategyRegistry registry = new PricingStrategyRegistry(List.of(s1, s2));

      assertThat(registry.resolve(PolicyType.TARGET_MARGIN)).isSameAs(s1);
      assertThat(registry.resolve(PolicyType.PRICE_CORRIDOR)).isSameAs(s2);
    }

    @Test
    @DisplayName("throws IllegalStateException on duplicate strategy type")
    void should_throwIllegalState_when_duplicateStrategyType() {
      PricingStrategy s1 = mockStrategy(PolicyType.TARGET_MARGIN);
      PricingStrategy s2 = mockStrategy(PolicyType.TARGET_MARGIN);

      assertThatThrownBy(() -> new PricingStrategyRegistry(List.of(s1, s2)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Duplicate PricingStrategy");
    }

    @Test
    @DisplayName("creates empty registry from empty list")
    void should_createEmptyRegistry_when_emptyList() {
      PricingStrategyRegistry registry = new PricingStrategyRegistry(List.of());

      assertThatThrownBy(() -> registry.resolve(PolicyType.TARGET_MARGIN))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private PricingStrategy mockStrategy(PolicyType type) {
    PricingStrategy strategy = mock(PricingStrategy.class);
    when(strategy.strategyType()).thenReturn(type);
    return strategy;
  }
}
