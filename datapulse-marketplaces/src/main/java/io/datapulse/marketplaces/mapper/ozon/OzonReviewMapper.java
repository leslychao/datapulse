package io.datapulse.marketplaces.mapper.ozon;

import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.marketplaces.dto.raw.ozon.OzonReviewRaw;

public final class OzonReviewMapper {

  private OzonReviewMapper() {
  }

  public static ReviewDto toDto(OzonReviewRaw r) {
    return new ReviewDto(
        r.review_id(),
        r.sku(),
        r.created_at(),
        r.updated_at(),
        r.rating(),
        r.text(),
        r.author(),
        r.answered(),
        r.answer_text(),
        r.answered_at(),
        r.photos_count()
    );
  }
}
