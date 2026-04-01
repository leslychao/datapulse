package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CostProfileCsvParserTest {

  @Nested
  @DisplayName("parse()")
  class Parse {

    @Test
    void should_parseValidCsv() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "SKU-001,150.50,RUB,2024-01-01\n"
              + "SKU-002,200.00,RUB,2024-02-01");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).hasSize(2);
      assertThat(result.getErrors()).isEmpty();

      var first = result.getRows().get(0);
      assertThat(first.skuCode()).isEqualTo("SKU-001");
      assertThat(first.costPrice()).isEqualByComparingTo(new BigDecimal("150.50"));
      assertThat(first.currency()).isEqualTo("RUB");
      assertThat(first.validFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void should_skipHeaderLine() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "SKU-001,100.00,RUB,2024-01-01");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).hasSize(1);
      assertThat(result.getRows().get(0).skuCode()).isEqualTo("SKU-001");
    }

    @Test
    void should_skipBlankLines() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "\n"
              + "SKU-001,100.00,RUB,2024-01-01\n"
              + "\n"
              + "SKU-002,200.00,RUB,2024-02-01");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).hasSize(2);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void should_reportError_when_wrongColumnCount() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "SKU-001,100.00,RUB");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).isEmpty();
      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).field()).isEqualTo("row");
    }

    @Test
    void should_reportError_when_invalidCostPrice() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "SKU-001,abc,RUB,2024-01-01");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).isEmpty();
      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).field()).isEqualTo("cost_price");
    }

    @Test
    void should_reportError_when_negativeCostPrice() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "SKU-001,-10,RUB,2024-01-01");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).isEmpty();
      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).field()).isEqualTo("cost_price");
      assertThat(result.getErrors().get(0).message()).contains("> 0");
    }

    @Test
    void should_reportError_when_invalidDate() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + "SKU-001,100.00,RUB,not-a-date");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).isEmpty();
      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).field()).isEqualTo("valid_from");
    }

    @Test
    void should_reportError_when_emptySkuCode() throws IOException {
      var input = toStream(
          "sku_code,cost_price,currency,valid_from\n"
              + ",100.00,RUB,2024-01-01");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).isEmpty();
      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).field()).isEqualTo("sku_code");
    }

    @Test
    void should_handleEmptyFile() throws IOException {
      var input = toStream("");

      var result = CostProfileCsvParser.parse(input);

      assertThat(result.getRows()).isEmpty();
      assertThat(result.getErrors()).isEmpty();
    }
  }

  private InputStream toStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
