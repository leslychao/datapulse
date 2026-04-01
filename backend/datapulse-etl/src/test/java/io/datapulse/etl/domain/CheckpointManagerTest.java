package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CheckpointManagerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CheckpointManager checkpointManager = new CheckpointManager(objectMapper);

  @Nested
  @DisplayName("parse()")
  class Parse {

    @Test
    void should_returnEmptyMap_when_checkpointIsNull() {
      var result = checkpointManager.parse(null);

      assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmptyMap_when_checkpointIsBlank() {
      var result = checkpointManager.parse("   ");

      assertThat(result).isEmpty();
    }

    @Test
    void should_parseCompletedEvent_from_validJson() {
      String json = """
          {"events":{"PRODUCT_DICT":{"status":"COMPLETED","last_cursor":"abc"}},"retry_count":1}""";

      var result = checkpointManager.parse(json);

      assertThat(result).containsKey(EtlEventType.PRODUCT_DICT);
      var entry = result.get(EtlEventType.PRODUCT_DICT);
      assertThat(entry.status()).isEqualTo(EventResultStatus.COMPLETED);
      assertThat(entry.lastCursor()).isEqualTo("abc");
    }

    @Test
    void should_parseFailedEvent_with_errorDetails() {
      String json = """
          {"events":{"SALES_FACT":{"status":"FAILED","error_type":"API_ERROR","error":"timeout"}}}""";

      var result = checkpointManager.parse(json);

      assertThat(result).containsKey(EtlEventType.SALES_FACT);
      var entry = result.get(EtlEventType.SALES_FACT);
      assertThat(entry.status()).isEqualTo(EventResultStatus.FAILED);
      assertThat(entry.errorType()).isEqualTo("API_ERROR");
      assertThat(entry.error()).isEqualTo("timeout");
    }

    @Test
    void should_returnEmptyMap_when_jsonIsMalformed() {
      var result = checkpointManager.parse("{not valid json!!}");

      assertThat(result).isEmpty();
    }

    @Test
    void should_skipUnknownEventTypes() {
      String json = """
          {"events":{"UNKNOWN_EVENT":{"status":"COMPLETED"},"PRODUCT_DICT":{"status":"COMPLETED"}}}""";

      var result = checkpointManager.parse(json);

      assertThat(result).containsKey(EtlEventType.PRODUCT_DICT);
      assertThat(result).doesNotContainKey(null);
      assertThat(result).hasSize(1);
    }
  }

  @Nested
  @DisplayName("serialize()")
  class Serialize {

    @Test
    void should_serializeCompletedResults() throws JsonProcessingException {
      Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
      results.put(EtlEventType.PRODUCT_DICT,
          EventResult.completed(EtlEventType.PRODUCT_DICT, List.of()));

      String json = checkpointManager.serialize(results, 0);

      assertThat(json).contains("\"COMPLETED\"");
      assertThat(json).contains("PRODUCT_DICT");
    }

    @Test
    void should_includeRetryCount() throws JsonProcessingException {
      Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
      results.put(EtlEventType.PRODUCT_DICT,
          EventResult.completed(EtlEventType.PRODUCT_DICT, List.of()));

      String json = checkpointManager.serialize(results, 2);

      assertThat(json).contains("\"retry_count\":2");
    }

    @Test
    void should_includeLastCursor_when_eventFailed() throws JsonProcessingException {
      var subResult = new SubSourceResult(
          "source", EventResultStatus.FAILED, "cursor-42",
          0, 0, 0, List.of("connection refused"));
      Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
      results.put(EtlEventType.SALES_FACT,
          EventResult.failed(EtlEventType.SALES_FACT, List.of(subResult)));

      String json = checkpointManager.serialize(results, 0);

      assertThat(json).contains("\"last_cursor\":\"cursor-42\"");
      assertThat(json).contains("\"error_type\":\"API_ERROR\"");
    }

    @Test
    void should_includeLastRetryAt_when_retryCountPositive() throws JsonProcessingException {
      Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
      results.put(EtlEventType.PRODUCT_DICT,
          EventResult.completed(EtlEventType.PRODUCT_DICT, List.of()));

      String json = checkpointManager.serialize(results, 1);

      assertThat(json).contains("last_retry_at");
    }
  }

  @Nested
  @DisplayName("extractRetryCount()")
  class ExtractRetryCount {

    @Test
    void should_returnZero_when_checkpointIsNull() {
      int count = checkpointManager.extractRetryCount(null);

      assertThat(count).isZero();
    }

    @Test
    void should_returnZero_when_checkpointHasNoRetryCount() {
      int count = checkpointManager.extractRetryCount("{\"events\":{}}");

      assertThat(count).isZero();
    }

    @Test
    void should_returnCount_when_present() {
      int count = checkpointManager.extractRetryCount(
          "{\"events\":{},\"retry_count\":3}");

      assertThat(count).isEqualTo(3);
    }
  }
}
