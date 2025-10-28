package io.datapulse.marketplaces.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbReviewRaw(
    String id,
    String supplierArticle,
    Long nmId,
    Integer productValuation,
    String text,
    String userName,
    OffsetDateTime createdDate,
    OffsetDateTime updatedDate,
    Boolean answerExists,
    String answerText,
    OffsetDateTime answerDate,
    Integer photoCount
) {}
