package io.datapulse.domain.dto;

import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReviewDto extends LongBaseDto {

  /** Идентификатор отзыва/вопроса (Ozon: id; WB: id) */
  private String reviewId;

  /** SKU/NM/offer — храним как строку (Ozon: sku/product_id; WB: nmId) */
  private String sku;

  /** Время создания (Ozon: created_at; WB: createdDate) */
  private OffsetDateTime createdAt;

  /** Время обновления/ответа (Ozon: updated_at? не всегда; WB: из answer при наличии) */
  private OffsetDateTime updatedAt;

  /** Рейтинг (Ozon: rating; WB: в questions нет рейтинга — null) */
  private Integer rating;

  /** Исходный текст отзыва/вопроса */
  private String text;

  /** Автор (Ozon: author; WB: может отсутствовать — null) */
  private String author;

  /** Есть ли ответ продавца */
  private Boolean answered;

  /** Текст ответа продавца (если есть) */
  private String answerText;

  /** Время ответа */
  private OffsetDateTime answeredAt;

  /** Кол-во фото (Ozon: attachments; WB: нет — null) */
  private Integer photosCount;
}
