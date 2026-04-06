package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.pricing.domain.CompetitorService.CsvRow;
import io.datapulse.pricing.persistence.CompetitorMatchRepository;
import io.datapulse.pricing.persistence.CompetitorObservationRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository;

@ExtendWith(MockitoExtension.class)
class CompetitorServiceCsvTest {

  @Mock
  private CompetitorMatchRepository matchRepository;

  @Mock
  private CompetitorObservationRepository observationRepository;

  @Mock
  private PricingDataReadRepository dataReadRepository;

  @InjectMocks
  private CompetitorService service;

  @Nested
  @DisplayName("CSV parsing")
  class CsvParsing {

    @Test
    @DisplayName("parses valid CSV with header")
    void should_parseValidCsv_when_headerPresent() {
      String csv = """
          sku_code,competitor_name,competitor_price
          12345,Store A,3500
          67890,Store B,2800.50
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0).skuCode()).isEqualTo("12345");
      assertThat(rows.get(0).competitorName()).isEqualTo("Store A");
      assertThat(rows.get(0).competitorPrice())
          .isEqualByComparingTo(new BigDecimal("3500"));
      assertThat(rows.get(1).skuCode()).isEqualTo("67890");
      assertThat(rows.get(1).competitorPrice())
          .isEqualByComparingTo(new BigDecimal("2800.50"));
    }

    @Test
    @DisplayName("parses valid CSV without header")
    void should_parseCsv_when_noHeader() {
      String csv = """
          12345,Store A,3500
          67890,Store B,2800
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0).skuCode()).isEqualTo("12345");
    }

    @Test
    @DisplayName("skips empty lines")
    void should_skipEmptyLines() {
      String csv = """
          12345,Store A,3500

          67890,Store B,2800

          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("skips rows with invalid price")
    void should_skipRows_when_priceInvalid() {
      String csv = """
          12345,Store A,not_a_number
          67890,Store B,2800
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).skuCode()).isEqualTo("67890");
    }

    @Test
    @DisplayName("skips rows with zero or negative price")
    void should_skipRows_when_priceNotPositive() {
      String csv = """
          12345,Store A,0
          67890,Store B,-100
          11111,Store C,500
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).skuCode()).isEqualTo("11111");
    }

    @Test
    @DisplayName("skips rows with missing fields")
    void should_skipRows_when_fieldsInsufficient() {
      String csv = """
          12345,Store A
          67890,Store B,2800
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("returns empty list for empty CSV")
    void should_returnEmpty_when_csvEmpty() {
      List<CsvRow> rows = service.parseCsv(toStream(""));

      assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("handles whitespace around values")
    void should_trimWhitespace() {
      String csv = """
          12345 , Store A , 3500
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).skuCode()).isEqualTo("12345");
      assertThat(rows.get(0).competitorName()).isEqualTo("Store A");
      assertThat(rows.get(0).competitorPrice())
          .isEqualByComparingTo(new BigDecimal("3500"));
    }

    @Test
    @DisplayName("skips rows with empty sku_code")
    void should_skipRows_when_skuCodeEmpty() {
      String csv = """
          ,Store A,3500
          67890,Store B,2800
          """;

      List<CsvRow> rows = service.parseCsv(toStream(csv));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).skuCode()).isEqualTo("67890");
    }
  }

  private InputStream toStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
