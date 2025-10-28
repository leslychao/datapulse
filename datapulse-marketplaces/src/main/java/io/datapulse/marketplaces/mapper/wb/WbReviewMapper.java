package io.datapulse.marketplaces.mapper.wb;

import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.marketplaces.dto.raw.wb.WbReviewRaw;

public final class WbReviewMapper {
  private WbReviewMapper() {}

  public static ReviewDto toDto(WbReviewRaw r) {
    String sku = r.supplierArticle()!=null ? r.supplierArticle() : String.valueOf(r.nmId());
    return new ReviewDto(
        r.id(),
        sku,
        r.createdDate(),
        r.updatedDate(),
        r.productValuation(),
        r.text(),
        r.userName(),
        r.answerExists(),
        r.answerText(),
        r.answerDate(),
        r.photoCount()
    );
  }
}
