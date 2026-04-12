package io.datapulse.etl.domain.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TailFieldExtractorTest {

  @TempDir Path tempDir;

  @Nested
  @DisplayName("extract()")
  class Extract {

    @Test
    void should_extractLastRrdId() throws IOException {
      var extractor = TailFieldExtractor.wbRrdId();
      Path file = writeContent("""
          [{"rrd_id": 100, "amount": 10},
           {"rrd_id": 200, "amount": 20},
           {"rrd_id": 999, "amount": 30}]""");

      assertThat(extractor.extract(file)).hasValue("999");
    }

    @Test
    void should_returnEmpty_when_fieldNotFound() throws IOException {
      var extractor = TailFieldExtractor.wbRrdId();
      Path file = writeContent("""
          [{"amount": 10}, {"amount": 20}]""");

      assertThat(extractor.extract(file)).isEmpty();
    }

    @Test
    void should_handleSmallFile() throws IOException {
      var extractor = TailFieldExtractor.wbRrdId();
      Path file = writeContent("""
          [{"rrd_id": 42}]""");

      assertThat(extractor.extract(file)).hasValue("42");
    }
  }

  @Nested
  @DisplayName("wbLastChangeDate()")
  class WbLastChangeDate {

    @Test
    void should_extractLastChangeDate_fromOrdersResponse() throws IOException {
      var extractor = TailFieldExtractor.wbLastChangeDate();
      Path file = writeContent("""
          [{"srid":"abc","lastChangeDate":"2025-12-01T07:51:35Z","nmId":100},
           {"srid":"def","lastChangeDate":"2025-12-01T12:54:35Z","nmId":200}]""");

      assertThat(extractor.extract(file)).hasValue("2025-12-01T12:54:35Z");
    }

    @Test
    void should_extractLastChangeDate_fromSalesResponse() throws IOException {
      var extractor = TailFieldExtractor.wbLastChangeDate();
      Path file = writeContent("""
          [{"saleID":"S123","lastChangeDate":"2026-01-15T10:00:00Z","forPay":100.5},
           {"saleID":"S124","lastChangeDate":"2026-01-15T14:30:00Z","forPay":200.0},
           {"saleID":"S125","lastChangeDate":"2026-01-15T18:45:22Z","forPay":50.0}]""");

      assertThat(extractor.extract(file)).hasValue("2026-01-15T18:45:22Z");
    }

    @Test
    void should_returnEmpty_when_noLastChangeDate() throws IOException {
      var extractor = TailFieldExtractor.wbLastChangeDate();
      Path file = writeContent("""
          [{"srid":"abc","nmId":100}]""");

      assertThat(extractor.extract(file)).isEmpty();
    }

    @Test
    void should_handleDateWithMilliseconds() throws IOException {
      var extractor = TailFieldExtractor.wbLastChangeDate();
      Path file = writeContent("""
          [{"lastChangeDate":"2026-03-10T08:15:30.123Z"}]""");

      assertThat(extractor.extract(file)).hasValue("2026-03-10T08:15:30.123Z");
    }
  }

  private Path writeContent(String content) throws IOException {
    Path file = tempDir.resolve("finance.json");
    Files.writeString(file, content);
    return file;
  }
}
