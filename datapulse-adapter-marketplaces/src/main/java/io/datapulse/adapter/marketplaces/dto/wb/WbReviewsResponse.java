package io.datapulse.adapter.marketplaces.dto.wb;

import java.time.LocalDate;
import java.util.List;

/**
 * /public/api/reviews — отзывы
 */
public record WbReviewsResponse(List<Item> data, String nextCursor) {

  public record Item(String sku, LocalDate date, Integer rating, String text, String author) {

  }
}
