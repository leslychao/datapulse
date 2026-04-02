package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.guard.PricingGuardChain.GuardChainResult;

@ExtendWith(MockitoExtension.class)
class PricingGuardChainTest {

  @Mock
  private PricingGuard guard1;

  @Mock
  private PricingGuard guard2;

  @Mock
  private PricingGuard guard3;

  private final PricingSignalSet signals = new PricingSignalSet(
      new BigDecimal("1000"), null, null, null,
      false, false, null, null, null, null, null, null, null, null);

  @Nested
  @DisplayName("evaluate — all pass")
  class AllPass {

    @Test
    @DisplayName("returns allPassed=true when all guards pass")
    void should_returnAllPassed_when_allGuardsPass() {
      configureGuard(guard1, 10, GuardResult.pass("g1"));
      configureGuard(guard2, 20, GuardResult.pass("g2"));
      configureGuard(guard3, 30, GuardResult.pass("g3"));

      PricingGuardChain chain = new PricingGuardChain(List.of(guard3, guard1, guard2));

      GuardChainResult result = chain.evaluate(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.allPassed()).isTrue();
      assertThat(result.blockingGuard()).isNull();
      assertThat(result.evaluations()).hasSize(3);
    }
  }

  @Nested
  @DisplayName("evaluate — one blocks")
  class OneBlocks {

    @Test
    @DisplayName("returns allPassed=false with blocking guard when one blocks")
    void should_returnBlocked_when_oneGuardBlocks() {
      GuardResult blockResult = GuardResult.block("g2", "pricing.guard.test");
      configureGuard(guard1, 10, GuardResult.pass("g1"));
      configureGuard(guard2, 20, blockResult);
      configureGuard(guard3, 30, GuardResult.pass("g3"));

      PricingGuardChain chain = new PricingGuardChain(List.of(guard1, guard2, guard3));

      GuardChainResult result = chain.evaluate(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard()).isSameAs(blockResult);
      assertThat(result.blockingGuard().guardName()).isEqualTo("g2");
    }

    @Test
    @DisplayName("short-circuits: guards after blocking one are not evaluated")
    void should_shortCircuit_when_guardBlocks() {
      configureGuard(guard1, 10, GuardResult.block("g1", "pricing.guard.test"));
      configureGuard(guard2, 20, GuardResult.pass("g2"));

      PricingGuardChain chain = new PricingGuardChain(List.of(guard1, guard2));

      GuardChainResult result = chain.evaluate(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.evaluations()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("ordering")
  class Ordering {

    @Test
    @DisplayName("evaluates guards in order of their order() value")
    void should_evaluateInOrder_when_guardsHaveDifferentOrder() {
      configureGuard(guard1, 30, GuardResult.pass("g_order30"));
      configureGuard(guard2, 10, GuardResult.pass("g_order10"));
      configureGuard(guard3, 20, GuardResult.pass("g_order20"));

      PricingGuardChain chain = new PricingGuardChain(List.of(guard1, guard2, guard3));

      GuardChainResult result = chain.evaluate(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.evaluations()).hasSize(3);
      assertThat(result.evaluations().get(0).name()).isEqualTo("g_order10");
      assertThat(result.evaluations().get(1).name()).isEqualTo("g_order20");
      assertThat(result.evaluations().get(2).name()).isEqualTo("g_order30");
    }
  }

  @Nested
  @DisplayName("empty chain")
  class EmptyChain {

    @Test
    @DisplayName("returns allPassed=true for empty guard list")
    void should_returnAllPassed_when_noGuards() {
      PricingGuardChain chain = new PricingGuardChain(List.of());

      GuardChainResult result = chain.evaluate(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.allPassed()).isTrue();
      assertThat(result.evaluations()).isEmpty();
    }
  }

  private void configureGuard(PricingGuard guard, int order, GuardResult result) {
    lenient().when(guard.order()).thenReturn(order);
    lenient().when(guard.check(any(), any(), any())).thenReturn(result);
  }
}
