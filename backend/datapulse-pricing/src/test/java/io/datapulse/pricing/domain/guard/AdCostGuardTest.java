package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class AdCostGuardTest {

    private final AdCostGuard guard = new AdCostGuard();

    @Test
    @DisplayName("guard name is 'ad_cost_drr'")
    void should_returnCorrectName() {
        assertThat(guard.guardName()).isEqualTo("ad_cost_drr");
    }

    @Test
    @DisplayName("order is 70")
    void should_returnOrder70() {
        assertThat(guard.order()).isEqualTo(70);
    }

    @Nested
    @DisplayName("when guard disabled")
    class Disabled {

        @Test
        @DisplayName("passes even when DRR exceeds threshold")
        void should_pass_when_guardDisabled() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.25"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, GuardConfig.DEFAULTS);

            assertThat(result.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("when guard enabled")
    class Enabled {

        private final GuardConfig config = adCostConfig(true, null);

        @Test
        @DisplayName("blocks price decrease when DRR exceeds threshold")
        void should_block_when_drrExceedsThreshold_and_priceDecreasing() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.20"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).isEqualTo("pricing.guard.ad_cost_drr.blocked");
            assertThat(result.args()).containsEntry("drrPct", "20.0");
            assertThat(result.args()).containsEntry("threshold", "15.0");
        }

        @Test
        @DisplayName("passes price increase even when DRR exceeds threshold (DD-AD-14)")
        void should_pass_when_priceIncreasing_even_highDrr() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.30"));
            BigDecimal targetPrice = new BigDecimal("1100");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passes when target price equals current price")
        void should_pass_when_priceUnchanged() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.30"));
            BigDecimal targetPrice = new BigDecimal("1000");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passes when DRR is below threshold")
        void should_pass_when_drrBelowThreshold() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.10"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passes when DRR equals threshold exactly")
        void should_pass_when_drrEqualsThreshold() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.15"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passes when adCostRatio is null")
        void should_pass_when_adCostRatioNull() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), null);
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passes when currentPrice is null")
        void should_pass_when_currentPriceNull() {
            PricingSignalSet signals = signals(null, new BigDecimal("0.25"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passes when targetPrice is null")
        void should_pass_when_targetPriceNull() {
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.25"));

            GuardResult result = guard.check(signals, null, config);

            assertThat(result.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("custom threshold")
    class CustomThreshold {

        @Test
        @DisplayName("respects custom DRR threshold")
        void should_useCustomThreshold_when_configured() {
            GuardConfig config = adCostConfig(true, new BigDecimal("0.30"));
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.25"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("blocks when DRR exceeds custom threshold")
        void should_block_when_drrExceedsCustomThreshold() {
            GuardConfig config = adCostConfig(true, new BigDecimal("0.10"));
            PricingSignalSet signals = signals(new BigDecimal("1000"), new BigDecimal("0.15"));
            BigDecimal targetPrice = new BigDecimal("900");

            GuardResult result = guard.check(signals, targetPrice, config);

            assertThat(result.passed()).isFalse();
            assertThat(result.args()).containsEntry("drrPct", "15.0");
            assertThat(result.args()).containsEntry("threshold", "10.0");
        }
    }

    private PricingSignalSet signals(BigDecimal currentPrice, BigDecimal adCostRatio) {
        return new PricingSignalSet(
                currentPrice, null, null, null,
                false, false,
                null, null, null, adCostRatio, null, null, null, null);
    }

    private GuardConfig adCostConfig(boolean enabled, BigDecimal thresholdPct) {
        return new GuardConfig(
                null, null, null, null, null, null, null, null, null,
                enabled, thresholdPct);
    }
}
