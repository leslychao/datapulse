package io.datapulse.domain.payload;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReviewPayload {
  private long accountId;
  private LocalDate date;
  private String sku;
  private int rating;
  private String reviewId;
}
