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
public class MismatchDetailRow {

  private long alertEventId;
  private String mismatchType;
  private Long offerId;
  private String offerName;
  private String skuCode;
  private String expectedValue;
  private String actualValue;
  private BigDecimal deltaPct;
  private String severity;
  private String status;
  private OffsetDateTime detectedAt;
  private String connectionName;
  private String marketplaceType;
  private Long connectionId;
  private OffsetDateTime acknowledgedAt;
  private String acknowledgedByName;
  private OffsetDateTime resolvedAt;
  private String resolvedReason;
  private String resolvedByName;

  private Long relatedActionId;
  private String relatedActionStatus;
  private BigDecimal relatedActionTargetPrice;
  private OffsetDateTime relatedActionExecutedAt;
  private String relatedActionReconciliationSource;
}
