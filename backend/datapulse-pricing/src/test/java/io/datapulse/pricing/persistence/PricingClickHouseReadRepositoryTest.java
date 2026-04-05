package io.datapulse.pricing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import io.datapulse.pricing.persistence.PricingClickHouseReadRepository.CommissionResult;

@ExtendWith(MockitoExtension.class)
class PricingClickHouseReadRepositoryTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  private PricingClickHouseReadRepository repository;

  @BeforeEach
  void setUp() {
    repository = new PricingClickHouseReadRepository(jdbcTemplate);
  }

  @Nested
  @DisplayName("findAvgCommissionPct — empty input guard")
  class FindAvgCommissionPct {

    @Test
    @DisplayName("returns empty map when sellerSkuIds is empty")
    void should_returnEmpty_when_emptyInput() {
      Map<Long, CommissionResult> result =
          repository.findAvgCommissionPct(1L, List.of(), 30, 5);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findCategoryAvgCommissionPct — empty input guard")
  class FindCategoryCommission {

    @Test
    @DisplayName("returns empty map when categories list is empty")
    void should_returnEmpty_when_emptyCategories() {
      Map<String, BigDecimal> result =
          repository.findCategoryAvgCommissionPct(1L, List.of(), 30);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findAvgLogisticsPerUnit — empty input guard")
  class FindAvgLogistics {

    @Test
    @DisplayName("returns empty map when sellerSkuIds is empty")
    void should_returnEmpty_when_emptyInput() {
      Map<Long, BigDecimal> result =
          repository.findAvgLogisticsPerUnit(1L, List.of(), 30);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findReturnRatePct — empty input guard")
  class FindReturnRate {

    @Test
    @DisplayName("returns empty map when sellerSkuIds is empty")
    void should_returnEmpty_when_emptyInput() {
      Map<Long, BigDecimal> result =
          repository.findReturnRatePct(1L, List.of(), 30);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findCategoriesBySellerSkuIds — empty input guard")
  class FindCategories {

    @Test
    @DisplayName("returns empty map when sellerSkuIds is empty")
    void should_returnEmpty_when_emptyInput() {
      Map<Long, String> result =
          repository.findCategoriesBySellerSkuIds(1L, List.of());

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findAdCostRatios — empty input guard")
  class FindAdCostRatios {

    @Test
    @DisplayName("returns empty map when marketplaceSkus is empty")
    void should_returnEmpty_when_emptyInput() {
      Map<String, BigDecimal> result =
          repository.findAdCostRatios(List.of(), 30);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("CommissionResult record")
  class CommissionResultRecord {

    @Test
    @DisplayName("stores commissionPct and transactionCount")
    void should_storeValues() {
      var cr = new CommissionResult(new BigDecimal("0.12"), 25);

      assertThat(cr.commissionPct()).isEqualByComparingTo(new BigDecimal("0.12"));
      assertThat(cr.transactionCount()).isEqualTo(25);
    }

    @Test
    @DisplayName("allows null commissionPct")
    void should_allowNull_when_divisionByZero() {
      var cr = new CommissionResult(null, 10);

      assertThat(cr.commissionPct()).isNull();
      assertThat(cr.transactionCount()).isEqualTo(10);
    }
  }
}
