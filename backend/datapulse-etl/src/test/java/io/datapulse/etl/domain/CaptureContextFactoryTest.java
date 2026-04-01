package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;

import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CaptureContextFactoryTest {

  @Nested
  @DisplayName("build()")
  class Build {

    @Test
    void should_buildContext_with_allFields() {
      var ctx = new IngestContext(1L, 100L, 1L, MarketplaceType.WB,
          Map.of(), "FULL_SYNC", EnumSet.allOf(EtlEventType.class), Map.of());

      var result = CaptureContextFactory.build(
          ctx, EtlEventType.SALES_FACT, "WbSalesFact");

      assertThat(result.jobExecutionId()).isEqualTo(1L);
      assertThat(result.connectionId()).isEqualTo(100L);
      assertThat(result.etlEvent()).isEqualTo(EtlEventType.SALES_FACT);
      assertThat(result.sourceId()).isEqualTo("WbSalesFact");
      assertThat(result.requestId()).hasSize(8);
    }
  }
}
