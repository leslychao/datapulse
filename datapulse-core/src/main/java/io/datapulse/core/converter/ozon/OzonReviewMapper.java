package io.datapulse.core.converter.ozon;

import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.domain.dto.raw.ozon.OzonReviewRaw;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface OzonReviewMapper {

  @Mappings({
      @Mapping(target = "reviewId", expression = "java(String.valueOf(src.id()))"),
      @Mapping(target = "sku", expression = "java(src.sku() != null ? String.valueOf(src.sku()) : (src.product_id() != null ? String.valueOf(src.product_id()) : null))"),
      @Mapping(target = "createdAt", source = "created_at"),
      @Mapping(target = "updatedAt", ignore = true),
      @Mapping(target = "rating", source = "rating"),
      @Mapping(target = "text", source = "text"),
      @Mapping(target = "author", source = "author"),
      @Mapping(target = "answered", expression = "java(src.status() != null && src.status().toLowerCase().contains(\"published\"))"),
      @Mapping(target = "answerText", ignore = true),
      @Mapping(target = "answeredAt", ignore = true),
      @Mapping(target = "photosCount", ignore = true)
  })
  ReviewDto toDto(OzonReviewRaw.Review src);
}
