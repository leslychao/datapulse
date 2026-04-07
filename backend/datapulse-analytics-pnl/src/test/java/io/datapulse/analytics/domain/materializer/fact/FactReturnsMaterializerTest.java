package io.datapulse.analytics.domain.materializer.fact;

import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.config.AnalyticsProperties;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FactReturnsMaterializer")
class FactReturnsMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private AnalyticsProperties properties;

  @InjectMocks
  private FactReturnsMaterializer materializer;

  @Test
  @DisplayName("should report table name as 'fact_returns'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("fact_returns");
  }

  @Test
  @DisplayName("should belong to FACT phase")
  void should_returnFactPhase() {
    assertThat(materializer.phase()).isEqualTo(MaterializationPhase.FACT);
  }

  @Nested
  @DisplayName("SQL queries")
  class SqlQueries {

    @Test
    @DisplayName("PG_QUERY should use COALESCE for fulfillment_type with cr as primary source")
    void should_coalesceReturnFulfillmentType_when_pgQuery() throws Exception {
      var field = FactReturnsMaterializer.class.getDeclaredField("PG_QUERY");
      field.setAccessible(true);
      String sql = (String) field.get(null);

      assertThat(sql).contains(
          "COALESCE(cr.fulfillment_type, co.fulfillment_type) AS fulfillment_type");
    }

    @Test
    @DisplayName("PG_QUERY should JOIN canonical_order for fulfillment_type fallback")
    void should_joinCanonicalOrder_when_pgQuery() throws Exception {
      var field = FactReturnsMaterializer.class.getDeclaredField("PG_QUERY");
      field.setAccessible(true);
      String sql = (String) field.get(null);

      assertThat(sql).contains("LEFT JOIN canonical_order co ON cr.canonical_order_id = co.id");
    }
  }
}
