package io.datapulse.domain.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonAnalyticsApiRaw(
    List<OzonAnalyticsApiDimensionRaw> dimensions,
    List<OzonAnalyticsApiMetricRaw> metrics
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OzonAnalyticsApiDimensionRaw(
      String id,
      String value
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OzonAnalyticsApiMetricRaw(
      String id,
      BigDecimal value
  ) {
  }
}
