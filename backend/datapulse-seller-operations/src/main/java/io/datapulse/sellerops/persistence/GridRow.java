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
public class GridRow {

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
    private String activePolicy;
    private String lastDecision;
    private String lastActionStatus;
    private String promoStatus;
    private boolean manualLock;
    private BigDecimal simulatedPrice;
    private BigDecimal simulatedDeltaPct;
    private OffsetDateTime lastSyncAt;
    private String bidPolicyName;
    private String bidStrategyType;
    private Integer currentBid;
    private String lastBidDecisionType;
    private boolean manualBidLock;
}
