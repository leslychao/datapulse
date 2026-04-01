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
public class ActionHistoryRow {

    private long actionId;
    private OffsetDateTime createdAt;
    private String status;
    private String executionMode;
    private BigDecimal targetPrice;
    private BigDecimal actualPrice;
    private String cancelReason;
    private String holdReason;
    private String manualOverrideReason;
}
