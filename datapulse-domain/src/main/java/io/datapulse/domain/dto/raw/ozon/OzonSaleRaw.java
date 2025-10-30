package io.datapulse.domain.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonSaleRaw(
    Result result
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      List<OzonAnalyticsRowRaw> data,
      List<Double> totals // если запрошены
  ) {

  }
}
