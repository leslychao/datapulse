package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record MismatchDetailResponse(
    long mismatchId,
    String type,
    String severity,
    String status,
    String expectedValue,
    String actualValue,
    BigDecimal deltaPct,
    OffsetDateTime detectedAt,
    Long relatedActionId,
    OfferInfo offer,
    String expectedSource,
    String actualSource,
    OffsetDateTime acknowledgedAt,
    String acknowledgedBy,
    String resolvedBy,
    OffsetDateTime resolvedAt,
    String resolutionNote,
    RelatedAction relatedAction,
    Thresholds thresholds,
    List<TimelineEvent> timeline
) {

  public record OfferInfo(
      long offerId,
      String offerName,
      String skuCode,
      String marketplaceType,
      String connectionName
  ) {}

  public record RelatedAction(
      long actionId,
      String status,
      BigDecimal targetPrice,
      OffsetDateTime executedAt,
      String reconciliationSource
  ) {}

  public record Thresholds(
      BigDecimal warningPct,
      BigDecimal criticalPct
  ) {}

  public record TimelineEvent(
      String eventType,
      OffsetDateTime timestamp,
      String description,
      String actor
  ) {}
}
