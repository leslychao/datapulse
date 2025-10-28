package io.datapulse.marketplaces.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonReviewRaw(
    Result result
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      List<Review> reviews,
      Integer total
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Review(
      Long id,
      Long product_id,
      Long sku,
      Integer rating,
      String status,              // published / pending / rejected ...
      OffsetDateTime created_at,
      String author,              // иногда может отсутствовать
      String text,
      String advantages,
      String disadvantages
  ) {}
}
