package io.datapulse.adapter.marketplaces.dto.response.ozon;

import java.time.LocalDate;
import java.util.List;

public record OzonReviewsResponse(List<Item> result, String pagingToken) {

  public record Item(String sku, LocalDate date, Integer rating, String text, String author) {

  }
}
