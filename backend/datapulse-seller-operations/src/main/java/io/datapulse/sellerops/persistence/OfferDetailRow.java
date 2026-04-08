package io.datapulse.sellerops.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferDetailRow {

  private long offerId;
  private long sellerSkuId;
  private String skuCode;
  private String productName;
  private String marketplaceType;
  private String connectionName;
  private String status;
  private String category;
  private BigDecimal currentPrice;
  private BigDecimal discountPrice;
  private BigDecimal costPrice;
  private BigDecimal marginPct;
  private Integer availableStock;
  private BigDecimal simulatedPrice;
  private BigDecimal simulatedDeltaPct;
  private OffsetDateTime lastSyncAt;

  private Long policyId;
  private String policyName;
  private String strategyType;
  private String policyExecutionMode;

  private Long decisionId;
  private String decisionType;
  private BigDecimal decisionCurrentPrice;
  private BigDecimal decisionTargetPrice;
  private String decisionExplanation;
  private OffsetDateTime decisionCreatedAt;

  private Long actionId;
  private String actionStatus;
  private BigDecimal actionTargetPrice;
  private String actionExecutionMode;
  private OffsetDateTime actionCreatedAt;

  private String promoParticipationStatus;
  private String promoCampaignName;
  private BigDecimal promoPrice;
  private OffsetDateTime promoEndsAt;

  private Long lockId;
  private BigDecimal lockedPrice;
  private String lockReason;
  private OffsetDateTime lockedAt;
}
