package io.datapulse.domain.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReviewDto extends LongBaseDto {

  private String reviewId;
  private String sku;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  private Integer rating;
  private String text;
  private String author;
  private Boolean answered;
  private String answerText;
  private OffsetDateTime answeredAt;
  private Integer photosCount;
}
