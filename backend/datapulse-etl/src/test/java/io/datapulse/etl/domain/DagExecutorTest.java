package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DagExecutorTest {

  @Mock private EventRunner eventRunner;

  private DagExecutor dagExecutor;

  @BeforeEach
  void setUp() {
    dagExecutor = new DagExecutor(eventRunner, Runnable::run);
  }

  @Nested
  @DisplayName("execute()")
  class Execute {

    @Test
    void should_executeAllEventsInDag_when_allSucceed() {
      when(eventRunner.run(any(EtlEventType.class), any(IngestContext.class)))
          .thenAnswer(inv -> {
            EtlEventType type = inv.getArgument(0);
            return EventResult.completed(type, List.of());
          });

      Map<EtlEventType, EventResult> results = dagExecutor.execute(buildContext());

      assertThat(results).hasSize(EtlEventType.values().length);
      for (EtlEventType type : EtlEventType.values()) {
        assertThat(results).containsKey(type);
        assertThat(results.get(type).status()).isEqualTo(EventResultStatus.COMPLETED);
      }
    }

    @Test
    void should_skipEvent_when_hardDependencyFailed() {
      when(eventRunner.run(any(EtlEventType.class), any(IngestContext.class)))
          .thenAnswer(inv -> {
            EtlEventType type = inv.getArgument(0);
            if (type == EtlEventType.CATEGORY_DICT) {
              return EventResult.failed(type, List.of(SubSourceResult.failed("cat", "err")));
            }
            return EventResult.completed(type, List.of());
          });

      Map<EtlEventType, EventResult> results = dagExecutor.execute(buildContext());

      assertThat(results.get(EtlEventType.PRODUCT_DICT).status())
          .isEqualTo(EventResultStatus.SKIPPED);
    }

    @Test
    void should_runEvent_when_softDependencyFailed() {
      when(eventRunner.run(any(EtlEventType.class), any(IngestContext.class)))
          .thenAnswer(inv -> {
            EtlEventType type = inv.getArgument(0);
            if (type == EtlEventType.SALES_FACT) {
              return EventResult.failed(type,
                  List.of(SubSourceResult.failed("sales", "timeout")));
            }
            return EventResult.completed(type, List.of());
          });

      Map<EtlEventType, EventResult> results = dagExecutor.execute(buildContext());

      assertThat(results.get(EtlEventType.FACT_FINANCE).status())
          .isNotEqualTo(EventResultStatus.SKIPPED);
      verify(eventRunner).run(eq(EtlEventType.FACT_FINANCE), any());
    }

    @Test
    void should_skipCheckpointCompletedEvents() {
      Map<EtlEventType, IngestContext.CheckpointEntry> checkpoint = Map.of(
          EtlEventType.CATEGORY_DICT,
          new IngestContext.CheckpointEntry(EventResultStatus.COMPLETED, null, null, null));

      IngestContext context = IngestContextFixtures.any(
          1L, 100L, 1L, MarketplaceType.WB,
          Map.of("apiToken", "test"), "FULL_SYNC",
          EnumSet.allOf(EtlEventType.class), checkpoint);

      when(eventRunner.run(any(EtlEventType.class), any(IngestContext.class)))
          .thenAnswer(inv -> {
            EtlEventType type = inv.getArgument(0);
            return EventResult.completed(type, List.of());
          });

      Map<EtlEventType, EventResult> results = dagExecutor.execute(context);

      verify(eventRunner, never()).run(eq(EtlEventType.CATEGORY_DICT), any());
      assertThat(results.get(EtlEventType.CATEGORY_DICT).status())
          .isEqualTo(EventResultStatus.COMPLETED);
    }

    @Test
    void should_cascadeSkip_when_hardDependencySkipped() {
      when(eventRunner.run(any(EtlEventType.class), any(IngestContext.class)))
          .thenAnswer(inv -> {
            EtlEventType type = inv.getArgument(0);
            if (type == EtlEventType.CATEGORY_DICT) {
              return EventResult.failed(type, List.of(SubSourceResult.failed("cat", "err")));
            }
            return EventResult.completed(type, List.of());
          });

      Map<EtlEventType, EventResult> results = dagExecutor.execute(buildContext());

      assertThat(results.get(EtlEventType.CATEGORY_DICT).status())
          .isEqualTo(EventResultStatus.FAILED);
      assertThat(results.get(EtlEventType.PRODUCT_DICT).status())
          .isEqualTo(EventResultStatus.SKIPPED);
      assertThat(results.get(EtlEventType.PRICE_SNAPSHOT).status())
          .isEqualTo(EventResultStatus.SKIPPED);
    }
  }

  private IngestContext buildContext() {
    return IngestContextFixtures.any(
        1L, 100L, 1L, MarketplaceType.WB,
        Map.of("apiToken", "test"), "FULL_SYNC",
        EnumSet.allOf(EtlEventType.class), Map.of());
  }
}
