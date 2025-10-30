package io.datapulse.domain.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonAnalyticsRowRaw(
    List<Dimension> dimensions,
    List<Double> metrics
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Dimension(
      String id,
      String name
  ) {}
}

