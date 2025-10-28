package io.datapulse.marketplaces.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonReviewRaw(
    String review_id,
    String sku,
    Integer rating,
    String text,
    String author,
    OffsetDateTime created_at,
    OffsetDateTime updated_at,
    Boolean answered,
    String answer_text,
    OffsetDateTime answered_at,
    Integer photos_count
) {}
