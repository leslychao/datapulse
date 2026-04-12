package io.datapulse.bidding.persistence;

import java.math.BigDecimal;

public record MarginMetricsRow(
    BigDecimal marginPct
) {
}
