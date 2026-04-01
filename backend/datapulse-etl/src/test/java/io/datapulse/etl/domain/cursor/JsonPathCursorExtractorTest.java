package io.datapulse.etl.domain.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonPathCursorExtractorTest {

  @TempDir Path tempDir;

  @Nested
  @DisplayName("extract()")
  class Extract {

    @Test
    void should_extractTopLevelCursor() throws IOException {
      var extractor = new JsonPathCursorExtractor("cursor");
      Path file = writeJson("""
          {"cursor":"abc123","data":[]}""");

      assertThat(extractor.extract(file)).hasValue("abc123");
    }

    @Test
    void should_extractNestedCursor() throws IOException {
      var extractor = new JsonPathCursorExtractor("result.cursor");
      Path file = writeJson("""
          {"result":{"cursor":"xyz789"},"data":[]}""");

      assertThat(extractor.extract(file)).hasValue("xyz789");
    }

    @Test
    void should_returnEmpty_when_fieldNotFound() throws IOException {
      var extractor = new JsonPathCursorExtractor("cursor");
      Path file = writeJson("""
          {"data":[]}""");

      assertThat(extractor.extract(file)).isEmpty();
    }

    @Test
    void should_returnEmpty_when_fieldIsNull() throws IOException {
      var extractor = new JsonPathCursorExtractor("cursor");
      Path file = writeJson("""
          {"cursor":null}""");

      assertThat(extractor.extract(file)).isEmpty();
    }

    @Test
    void should_handleLargeFile() throws IOException {
      var extractor = new JsonPathCursorExtractor("cursor");
      String padding = "x".repeat(1000);
      Path file = writeJson("""
          {"cursor":"found","padding":"%s"}""".formatted(padding));

      assertThat(extractor.extract(file)).hasValue("found");
    }
  }

  private Path writeJson(String content) throws IOException {
    Path file = tempDir.resolve("response.json");
    Files.writeString(file, content);
    return file;
  }
}
