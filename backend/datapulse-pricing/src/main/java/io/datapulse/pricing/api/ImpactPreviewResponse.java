package io.datapulse.pricing.api;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;

public record ImpactPreviewResponse(
    ImpactPreviewSummary summary,
    Page<ImpactPreviewOfferResponse> offers,
    String narrative,
    NarrativeStatus narrativeStatus
) {

  public enum NarrativeStatus {
    PENDING, READY, UNAVAILABLE
  }

  public ImpactPreviewResponse(ImpactPreviewSummary summary,
                               Page<ImpactPreviewOfferResponse> offers) {
    this(summary, offers, null, NarrativeStatus.UNAVAILABLE);
  }

  public record ImpactPreviewSummary(
      int totalOffers,
      int eligibleCount,
      int changeCount,
      int skipCount,
      int holdCount,
      BigDecimal avgPriceChangePct,
      BigDecimal maxPriceChangePct,
      BigDecimal minMarginAfter
  ) {
  }

  public record ImpactPreviewOfferResponse(
      String offerName,
      String sellerSku,
      BigDecimal currentPrice,
      BigDecimal targetPrice,
      BigDecimal changePct,
      BigDecimal changeAmount,
      String decisionType,
      String skipReason
  ) {
  }
}
