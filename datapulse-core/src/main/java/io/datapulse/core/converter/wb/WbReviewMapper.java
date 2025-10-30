package io.datapulse.core.converter.wb;

import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.domain.dto.raw.wb.WbReviewRaw; // = бывший WbQuestionRaw
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface WbReviewMapper {

  @Mappings({
      @Mapping(target = "reviewId", source = "id"),
      @Mapping(target = "sku", expression = "java(src.productDetails() != null ? String.valueOf(src.productDetails().nmId()) : null)"),
      @Mapping(target = "createdAt", source = "createdDate"),
      @Mapping(target = "updatedAt", ignore = true), // у WB нет явного updated; можно оставить null
      @Mapping(target = "rating", ignore = true),    // в questions нет рейтинга
      @Mapping(target = "text", source = "text"),
      @Mapping(target = "author", ignore = true),
      @Mapping(target = "answered", expression = "java(src.answer() != null && src.answer().text() != null && !src.answer().text().isBlank())"),
      @Mapping(target = "answerText", expression = "java(src.answer() != null ? src.answer().text() : null)"),
      @Mapping(target = "answeredAt", ignore = true),
      @Mapping(target = "photosCount", ignore = true)
  })
  ReviewDto toDto(WbReviewRaw src);
}
