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
      var extractor = new TailFieldExtractor("rrd_id");
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

  private Path writeContent(String content) throws IOException {
    Path file = tempDir.resolve("finance.json");
    Files.writeString(file, content);
    return file;
  }
}
