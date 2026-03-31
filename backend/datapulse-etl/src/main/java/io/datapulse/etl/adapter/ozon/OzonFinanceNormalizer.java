package io.datapulse.etl.adapter.ozon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.datapulse.etl.adapter.ozon.dto.OzonFinanceTransaction;
import io.datapulse.etl.adapter.util.OzonTimestampParser;
import io.datapulse.etl.domain.FinanceEntryType;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DD-4: Sign as-is (positive = credit, negative = debit).
 * DD-6: Finance timestamps parsed as Moscow TZ (UTC+3).
 * operation_type → FinanceEntryType mapping, unmapped → OTHER + log.warn.
 */
@Slf4j
@Service
public class OzonFinanceNormalizer {

    public NormalizedFinanceItem normalizeFinanceTransaction(OzonFinanceTransaction tx) {
        FinanceEntryType entryType = FinanceEntryType.fromOzonOperationType(tx.operationType());
        if (entryType == FinanceEntryType.OTHER && tx.operationType() != null) {
            log.warn("Unmapped Ozon finance operation_type: type={}, operationId={}",
                    tx.operationType(), tx.operationId());
        }

        OffsetDateTime entryDate = OzonTimestampParser.parseFinanceTimestamp(tx.operationDate());
        String postingNumber = tx.posting() != null ? tx.posting().postingNumber() : null;
        String warehouseId = tx.posting() != null
                ? String.valueOf(tx.posting().warehouseId())
                : null;

        BigDecimal revenueAmount = safe(tx.accrualsForSale());
        BigDecimal commissionAmount = safe(tx.saleCommission()).negate();
        BigDecimal logisticsCost = BigDecimal.ZERO;
        BigDecimal storageCost = BigDecimal.ZERO;
        BigDecimal otherCharges = BigDecimal.ZERO;

        if (tx.services() != null) {
            for (var service : tx.services()) {
                BigDecimal price = safe(service.price());
                switch (resolveServiceCategory(service.name())) {
                    case LOGISTICS -> logisticsCost = logisticsCost.add(price);
                    case STORAGE -> storageCost = storageCost.add(price);
                    default -> otherCharges = otherCharges.add(price);
                }
            }
        }

        String sellerSku = null;
        if (tx.items() != null && !tx.items().isEmpty()) {
            sellerSku = String.valueOf(tx.items().get(0).sku());
        }

        return new NormalizedFinanceItem(
                String.valueOf(tx.operationId()),
                entryType.name(),
                postingNumber,
                null,
                sellerSku,
                warehouseId,
                revenueAmount,
                commissionAmount,
                BigDecimal.ZERO,
                logisticsCost,
                storageCost,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                otherCharges,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                safe(tx.amount()),
                "RUB",
                entryDate,
                "ORDER"
        );
    }

    private static BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private enum ServiceCategory {
        LOGISTICS, STORAGE, OTHER
    }

    private static ServiceCategory resolveServiceCategory(String serviceName) {
        if (serviceName == null) {
            return ServiceCategory.OTHER;
        }
        String lower = serviceName.toLowerCase();
        if (lower.contains("logistic") || lower.contains("delivery")
                || lower.contains("fulfillment") || lower.contains("crossdock")
                || lower.contains("last_mile") || lower.contains("direct_flow")
                || lower.contains("return_flow") || lower.contains("pickup")) {
            return ServiceCategory.LOGISTICS;
        }
        if (lower.contains("storage")) {
            return ServiceCategory.STORAGE;
        }
        return ServiceCategory.OTHER;
    }
}
