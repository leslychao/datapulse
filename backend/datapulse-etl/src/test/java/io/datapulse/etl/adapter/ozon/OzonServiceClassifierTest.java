package io.datapulse.etl.adapter.ozon;

import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.etl.domain.FinanceEntryType.MeasureColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OzonServiceClassifierTest {

  @Nested
  @DisplayName("classify()")
  class Classify {

    @Test
    void should_returnLogistics_for_directFlowLogistic() {
      var result = OzonServiceClassifier.classify("MarketplaceServiceItemDirectFlowLogistic");

      assertThat(result).isEqualTo(MeasureColumn.LOGISTICS);
    }

    @Test
    void should_returnLogistics_for_delivToCustomer() {
      var result = OzonServiceClassifier.classify("MarketplaceServiceItemDelivToCustomer");

      assertThat(result).isEqualTo(MeasureColumn.LOGISTICS);
    }

    @Test
    void should_returnAcquiring_for_acquiringOperation() {
      var result = OzonServiceClassifier.classify("MarketplaceRedistributionOfAcquiringOperation");

      assertThat(result).isEqualTo(MeasureColumn.ACQUIRING);
    }

    @Test
    void should_returnMarketplaceCommission_for_brandCommission() {
      var result = OzonServiceClassifier.classify("MarketplaceServiceBrandCommission");

      assertThat(result).isEqualTo(MeasureColumn.MARKETPLACE_COMMISSION);
    }

    @Test
    void should_returnPenalties_for_disposalDetailed() {
      var result = OzonServiceClassifier.classify("MarketplaceServiceItemDisposalDetailed");

      assertThat(result).isEqualTo(MeasureColumn.PENALTIES);
    }

    @Test
    void should_returnOther_for_unknownServiceName() {
      var result = OzonServiceClassifier.classify("SomeCompletelyUnknownService");

      assertThat(result).isEqualTo(MeasureColumn.OTHER);
    }

    @Test
    void should_returnOther_when_serviceNameIsNull() {
      var result = OzonServiceClassifier.classify(null);

      assertThat(result).isEqualTo(MeasureColumn.OTHER);
    }
  }
}
