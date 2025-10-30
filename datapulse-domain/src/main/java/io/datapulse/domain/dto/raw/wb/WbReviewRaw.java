package io.datapulse.domain.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbReviewRaw(
    String id,
    String text,
    OffsetDateTime createdDate,
    String state,
    Answer answer,
    ProductDetails productDetails,
    Boolean wasViewed,
    Boolean isWarned
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductDetails(
        Long imtId,
        Long nmId,
        String productName,
        String supplierArticle,
        String supplierName,
        String brandName
    ) {}

    // в сэмпле answer=null; структура ответа у WB есть, но может варьироваться — оставляем «плоский» минимум
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(
        String text
    ) {}
}
