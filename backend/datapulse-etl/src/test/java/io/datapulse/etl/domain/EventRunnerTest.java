package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventRunnerTest {

  @Mock private EventSourceRegistry registry;
  @InjectMocks private EventRunner eventRunner;

  @Nested
  @DisplayName("run()")
  class Run {

    @Test
    void should_returnCompleted_when_sourceExecutesSuccessfully() {
      EventSource source = mockSource(List.of(
          SubSourceResult.success("WbPriceSnapshot", 2, 100)));
      when(registry.resolve(MarketplaceType.WB, EtlEventType.PRICE_SNAPSHOT))
          .thenReturn(Optional.of(source));

      EventResult result = eventRunner.run(EtlEventType.PRICE_SNAPSHOT, buildContext());

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED);
      assertThat(result.subSourceResults()).hasSize(1);
    }

    @Test
    void should_returnSkipped_when_noSourceRegistered() {
      when(registry.resolve(any(), any())).thenReturn(Optional.empty());

      EventResult result = eventRunner.run(EtlEventType.PRICE_SNAPSHOT, buildContext());

      assertThat(result.status()).isEqualTo(EventResultStatus.SKIPPED);
    }

    @Test
    void should_returnFailed_when_sourceThrowsException() {
      EventSource source = mockSourceThrowing(new RuntimeException("Connection refused"));
      when(registry.resolve(MarketplaceType.WB, EtlEventType.SALES_FACT))
          .thenReturn(Optional.of(source));

      EventResult result = eventRunner.run(EtlEventType.SALES_FACT, buildContext());

      assertThat(result.status()).isEqualTo(EventResultStatus.FAILED);
      assertThat(result.subSourceResults()).isNotEmpty();
      assertThat(result.subSourceResults().get(0).errors())
          .contains("Connection refused");
    }

    @Test
    void should_deriveStatus_from_subSources() {
      EventSource source = mockSource(List.of(
          SubSourceResult.success("sub1", 1, 50),
          SubSourceResult.failed("sub2", "partial failure")));
      when(registry.resolve(MarketplaceType.WB, EtlEventType.PRODUCT_DICT))
          .thenReturn(Optional.of(source));

      EventResult result = eventRunner.run(EtlEventType.PRODUCT_DICT, buildContext());

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED_WITH_ERRORS);
    }
  }

  private IngestContext buildContext() {
    return IngestContextFixtures.any(
        1L, 100L, 1L, MarketplaceType.WB,
        Map.of("apiToken", "test"), "FULL_SYNC",
        EnumSet.allOf(EtlEventType.class), Map.of());
  }

  private EventSource mockSource(List<SubSourceResult> results) {
    return new EventSource() {
      @Override
      public MarketplaceType marketplace() {
        return MarketplaceType.WB;
      }

      @Override
      public EtlEventType eventType() {
        return EtlEventType.PRICE_SNAPSHOT;
      }

      @Override
      public List<SubSourceResult> execute(IngestContext context) {
        return results;
      }
    };
  }

  private EventSource mockSourceThrowing(RuntimeException exception) {
    return new EventSource() {
      @Override
      public MarketplaceType marketplace() {
        return MarketplaceType.WB;
      }

      @Override
      public EtlEventType eventType() {
        return EtlEventType.SALES_FACT;
      }

      @Override
      public List<SubSourceResult> execute(IngestContext context) {
        throw exception;
      }
    };
  }
}
