package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SubSourceResultTest {

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    void should_createSuccess() {
      var result = SubSourceResult.success("source", 5, 100);

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.errors()).isEmpty();
      assertThat(result.sourceId()).isEqualTo("source");
      assertThat(result.pagesProcessed()).isEqualTo(5);
      assertThat(result.recordsProcessed()).isEqualTo(100);
    }

    @Test
    void should_createFailed() {
      var result = SubSourceResult.failed("source", "error msg");

      assertThat(result.status()).isEqualTo(EventResultStatus.FAILED);
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.errors()).containsExactly("error msg");
      assertThat(result.pagesProcessed()).isZero();
      assertThat(result.recordsProcessed()).isZero();
    }

    @Test
    void should_createPartial() {
      var result = SubSourceResult.partial("source", "cursor", 3, 50, 5, List.of("err"));

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED_WITH_ERRORS);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.lastCursor()).isEqualTo("cursor");
      assertThat(result.pagesProcessed()).isEqualTo(3);
      assertThat(result.recordsProcessed()).isEqualTo(50);
      assertThat(result.recordsSkipped()).isEqualTo(5);
      assertThat(result.errors()).containsExactly("err");
    }
  }

  @Nested
  @DisplayName("EventResult.fromSubSources()")
  class EventResultFromSubSources {

    @Test
    void should_returnCompleted_when_allSuccess() {
      var subSources = List.of(
          SubSourceResult.success("s1", 1, 10),
          SubSourceResult.success("s2", 2, 20));

      var result = EventResult.fromSubSources(EtlEventType.SALES_FACT, subSources);

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED);
    }

    @Test
    void should_returnFailed_when_allFailed() {
      var subSources = List.of(
          SubSourceResult.failed("s1", "err1"),
          SubSourceResult.failed("s2", "err2"));

      var result = EventResult.fromSubSources(EtlEventType.SALES_FACT, subSources);

      assertThat(result.status()).isEqualTo(EventResultStatus.FAILED);
    }

    @Test
    void should_returnCompletedWithErrors_when_mixed() {
      var subSources = List.of(
          SubSourceResult.success("s1", 1, 10),
          SubSourceResult.failed("s2", "err"));

      var result = EventResult.fromSubSources(EtlEventType.SALES_FACT, subSources);

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED_WITH_ERRORS);
    }

    @Test
    void should_returnCompletedWithErrors_when_anyPartial() {
      var subSources = List.of(
          SubSourceResult.success("s1", 1, 10),
          SubSourceResult.partial("s2", "cur", 2, 20, 3, List.of("warn")));

      var result = EventResult.fromSubSources(EtlEventType.SALES_FACT, subSources);

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED_WITH_ERRORS);
    }
  }
}
