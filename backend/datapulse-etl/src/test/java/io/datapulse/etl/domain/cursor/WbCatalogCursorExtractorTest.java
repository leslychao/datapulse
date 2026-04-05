package io.datapulse.etl.domain.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.datapulse.etl.domain.cursor.WbCatalogCursorExtractor.WbCatalogCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WbCatalogCursorExtractorTest {

  @TempDir Path tempDir;

  @Test
  void extractsAllThreeFields() throws IOException {
    Path file = tempDir.resolve("response.json");
    Files.writeString(file, """
        {
          "cards": [],
          "cursor": {
            "updatedAt": "2025-12-16T17:40:20Z",
            "nmID": 274849,
            "total": 100
          }
        }
        """);

    var result = WbCatalogCursorExtractor.INSTANCE.extract(file);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("2025-12-16T17:40:20Z|274849|100");
  }

  @Test
  void returnsEmptyWhenNoCursorObject() throws IOException {
    Path file = tempDir.resolve("response.json");
    Files.writeString(file, """
        {"cards": []}
        """);

    var result = WbCatalogCursorExtractor.INSTANCE.extract(file);
    assertThat(result).isEmpty();
  }

  @Test
  void parsesCompositeString() {
    WbCatalogCursor cursor =
        WbCatalogCursorExtractor.parse("2025-12-16T17:40:20Z|274849|42");
    assertThat(cursor.updatedAt()).isEqualTo("2025-12-16T17:40:20Z");
    assertThat(cursor.nmId()).isEqualTo(274849L);
    assertThat(cursor.total()).isEqualTo(42);
  }

  @Test
  void parseHandlesNullAndEmpty() {
    WbCatalogCursor fromNull = WbCatalogCursorExtractor.parse(null);
    assertThat(fromNull.updatedAt()).isEmpty();
    assertThat(fromNull.nmId()).isZero();
    assertThat(fromNull.total()).isZero();

    WbCatalogCursor fromEmpty = WbCatalogCursorExtractor.parse("");
    assertThat(fromEmpty.updatedAt()).isEmpty();
    assertThat(fromEmpty.nmId()).isZero();
    assertThat(fromEmpty.total()).isZero();
  }

  @Test
  void extractsZeroTotal_forLastPage() throws IOException {
    Path file = tempDir.resolve("response.json");
    Files.writeString(file, """
        {
          "cards": [{"nmID": 1}],
          "cursor": {
            "updatedAt": "2025-12-16T17:40:20Z",
            "nmID": 1,
            "total": 1
          }
        }
        """);

    var result = WbCatalogCursorExtractor.INSTANCE.extract(file);
    assertThat(result).isPresent();

    WbCatalogCursor cursor = WbCatalogCursorExtractor.parse(result.get());
    assertThat(cursor.total()).isEqualTo(1);
  }
}
