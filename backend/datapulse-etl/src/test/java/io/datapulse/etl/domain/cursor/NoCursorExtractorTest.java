package io.datapulse.etl.domain.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NoCursorExtractorTest {

  private final NoCursorExtractor extractor = NoCursorExtractor.INSTANCE;

  @Test
  void should_returnEmpty_always() {
    assertThat(extractor.extract(Path.of("any-file"))).isEmpty();
  }
}
