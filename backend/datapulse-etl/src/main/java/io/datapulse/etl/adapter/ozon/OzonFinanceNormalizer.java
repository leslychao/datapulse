package io.datapulse.etl.adapter.ozon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import io.datapulse.etl.adapter.ozon.dto.OzonFinancePosting;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceTransaction;
import io.datapulse.etl.adapter.util.OzonTimestampParser;
import io.datapulse.etl.domain.FinanceEntryType;
import io.datapulse.etl.domain.FinanceEntryType.MeasureColumn;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ozon finance DTO → NormalizedFinanceItem.
 *
 * <p>DD-4: Signs preserved as-is (positive = credit, negative = debit).
 * DD-6: Finance timestamps parsed as Moscow TZ (UTC+3).
 * Posting/order resolution per etl-pipeline.md §Canonical finance resolution rules.</p>
 *
 * <p>For breakdown operations (SALE_ACCRUAL, RETURN_REVERSAL, STORNO_CORRECTION):
 * accruals_for_sale + sale_commission + services[] are decomposed into measure columns.
 * For standalone operations: {@code amount} routes to the primary measure column.</p>
 */
@Slf4j
@Service
public class OzonFinanceNormalizer {

    /**
     * Posting format: digits-digits-digits (e.g. "87621408-0010-1").
     * Numeric-only strings (CPC campaign IDs) and empty strings are NOT posting format.
     */
    private static final Pattern POSTING_FORMAT = Pattern.compile("^\\d+-\\d+-\\d+$");

    public NormalizedFinanceItem normalizeFinanceTransaction(OzonFinanceTransaction tx) {
        FinanceEntryType entryType = FinanceEntryType.fromOzonOperationType(tx.operationType());
        if (entryType == FinanceEntryType.OTHER && tx.operationType() != null) {
            log.warn("Unmapped Ozon finance operation_type: type={}, operationId={}",
                    tx.operationType(), tx.operationId());
        }

        OffsetDateTime entryDate = OzonTimestampParser.parseFinanceTimestamp(tx.operationDate());

        String rawPosting = tx.posting() != null ? tx.posting().postingNumber() : null;
        String warehouseExternalId = tx.posting() != null && tx.posting().warehouseId() != 0
                ? String.valueOf(tx.posting().warehouseId())
                : null;

        PostingResolution resolved = resolvePostingAndOrder(rawPosting);

        String marketplaceSku = null;
        if (tx.items() != null && !tx.items().isEmpty()) {
            marketplaceSku = String.valueOf(tx.items().get(0).sku());
        }

        String fulfillmentType = resolveOzonFulfillment(tx.posting());

        MeasureAccumulator measures = entryType.hasOzonBreakdown()
                ? buildBreakdownMeasures(entryType, tx)
                : buildStandaloneMeasures(entryType, safe(tx.amount()));

        return new NormalizedFinanceItem(
                String.valueOf(tx.operationId()),
                entryType,
                resolved.postingId,
                resolved.orderId,
                null,
                marketplaceSku,
                warehouseExternalId,
                fulfillmentType,
                measures.revenue,
                measures.marketplaceCommission,
                measures.acquiring,
                measures.logistics,
                measures.storage,
                measures.penalties,
                measures.acceptance,
                measures.marketing,
                measures.other,
                measures.compensation,
                measures.refund,
                safe(tx.amount()),
                "RUB",
                entryDate
        );
    }

    /**
     * Breakdown operations have accruals_for_sale, sale_commission and services[] populated.
     * Route accruals to revenue or refund depending on entry_type, commission to marketplace_commission,
     * and classify each service into the correct measure column.
     */
    private MeasureAccumulator buildBreakdownMeasures(FinanceEntryType entryType,
                                                      OzonFinanceTransaction tx) {
        var m = new MeasureAccumulator();
        BigDecimal accruals = safe(tx.accrualsForSale());
        BigDecimal commission = safe(tx.saleCommission()).negate();

        switch (entryType) {
            case SALE_ACCRUAL -> {
                m.revenue = accruals;
                m.marketplaceCommission = commission;
            }
            case RETURN_REVERSAL -> {
                m.refund = accruals;
                m.marketplaceCommission = commission;
            }
            case STORNO_CORRECTION -> {
                m.refund = accruals;
                m.marketplaceCommission = commission;
            }
            default -> m.revenue = accruals;
        }

        if (tx.services() != null) {
            for (var service : tx.services()) {
                BigDecimal price = safe(service.price());
                addToMeasure(m, OzonServiceClassifier.classify(service.name()), price);
            }
        }
        return m;
    }

    /**
     * Standalone operations (ACQUIRING, STORAGE, penalties, marketing, etc.)
     * have no breakdown — route the full {@code amount} to the primary measure column.
     */
    private MeasureAccumulator buildStandaloneMeasures(FinanceEntryType entryType,
                                                       BigDecimal amount) {
        var m = new MeasureAccumulator();
        addToMeasure(m, entryType.primaryMeasure(), amount);
        return m;
    }

    private static void addToMeasure(MeasureAccumulator m, MeasureColumn column, BigDecimal value) {
        switch (column) {
            case REVENUE -> m.revenue = m.revenue.add(value);
            case REFUND -> m.refund = m.refund.add(value);
            case MARKETPLACE_COMMISSION -> m.marketplaceCommission = m.marketplaceCommission.add(value);
            case ACQUIRING -> m.acquiring = m.acquiring.add(value);
            case LOGISTICS -> m.logistics = m.logistics.add(value);
            case STORAGE -> m.storage = m.storage.add(value);
            case PENALTIES -> m.penalties = m.penalties.add(value);
            case ACCEPTANCE -> m.acceptance = m.acceptance.add(value);
            case MARKETING -> m.marketing = m.marketing.add(value);
            case COMPENSATION -> m.compensation = m.compensation.add(value);
            case OTHER -> m.other = m.other.add(value);
        }
    }

    private static String resolveOzonFulfillment(OzonFinancePosting posting) {
        if (posting == null || posting.deliverySchema() == null
                || posting.deliverySchema().isBlank()) {
            return null;
        }
        return posting.deliverySchema().toUpperCase();
    }

    /**
     * Resolves posting_id and order_id from the raw posting_number.
     * <ul>
     *   <li>Posting format (digits-digits-digits): posting_id = as-is, order_id = strip last -N segment</li>
     *   <li>Acquiring format (digits-digits, no third segment): posting_id = NULL, order_id = as-is</li>
     *   <li>Standalone (empty or non-posting format like campaign IDs): both NULL</li>
     * </ul>
     */
    private static PostingResolution resolvePostingAndOrder(String rawPosting) {
        if (rawPosting == null || rawPosting.isBlank()) {
            return new PostingResolution(null, null);
        }
        if (POSTING_FORMAT.matcher(rawPosting).matches()) {
            String orderId = rawPosting.substring(0, rawPosting.lastIndexOf('-'));
            return new PostingResolution(rawPosting, orderId);
        }
        if (rawPosting.contains("-")) {
            return new PostingResolution(null, rawPosting);
        }
        return new PostingResolution(null, null);
    }

    private static BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private record PostingResolution(String postingId, String orderId) {
    }

    private static class MeasureAccumulator {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal marketplaceCommission = BigDecimal.ZERO;
        BigDecimal acquiring = BigDecimal.ZERO;
        BigDecimal logistics = BigDecimal.ZERO;
        BigDecimal storage = BigDecimal.ZERO;
        BigDecimal penalties = BigDecimal.ZERO;
        BigDecimal acceptance = BigDecimal.ZERO;
        BigDecimal marketing = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal compensation = BigDecimal.ZERO;
    }
}
