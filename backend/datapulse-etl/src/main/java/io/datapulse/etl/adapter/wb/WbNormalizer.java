package io.datapulse.etl.adapter.wb;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.adapter.util.WbTimestampParser;
import io.datapulse.etl.adapter.wb.dto.WbCatalogCard;
import io.datapulse.etl.adapter.wb.dto.WbFinanceRow;
import io.datapulse.etl.adapter.wb.dto.WbOffice;
import io.datapulse.etl.adapter.wb.dto.WbOrderItem;
import io.datapulse.etl.adapter.wb.dto.WbPriceGood;
import io.datapulse.etl.adapter.wb.dto.WbPromotionDto;
import io.datapulse.etl.adapter.wb.dto.WbPromotionNomenclatureDto;
import io.datapulse.etl.adapter.wb.dto.WbReturnItem;
import io.datapulse.etl.adapter.wb.dto.WbSaleItem;
import io.datapulse.etl.adapter.wb.dto.WbStockItem;
import io.datapulse.etl.domain.FinanceEntryType;
import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.domain.normalized.NormalizedCategory;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.domain.normalized.NormalizedOrderItem;
import io.datapulse.etl.domain.normalized.NormalizedPriceItem;
import io.datapulse.etl.domain.normalized.NormalizedPromoCampaign;
import io.datapulse.etl.domain.normalized.NormalizedPromoProduct;
import io.datapulse.etl.domain.normalized.NormalizedReturnItem;
import io.datapulse.etl.domain.normalized.NormalizedSaleItem;
import io.datapulse.etl.domain.normalized.NormalizedStockItem;
import io.datapulse.etl.domain.normalized.NormalizedWarehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stateless normalizer: transforms WB provider DTOs into marketplace-agnostic normalized records.
 * Key responsibilities:
 * <ul>
 *   <li>DD-7: Sign conventions — debit fields are multiplied by -1</li>
 *   <li>DD-9: Dual-format timestamp parsing (ISO 8601 / date-only)</li>
 *   <li>DD-8: Composite finance row model — one WB row → one NormalizedFinanceItem</li>
 *   <li>DD-17: sale_dt nullability — fallback to rr_dt</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WbNormalizer {

    private static final String MARKETPLACE_WB = "WB";

    private final ObjectMapper objectMapper;

    public NormalizedCatalogItem normalizeCatalogCard(WbCatalogCard card) {
        String barcode = extractFirstBarcode(card);
        return new NormalizedCatalogItem(
                card.vendorCode(),
                String.valueOf(card.nmID()),
                card.nmUUID(),
                card.title(),
                card.brand(),
                card.subjectName(),
                barcode,
                "ACTIVE"
        );
    }

    public NormalizedPriceItem normalizePrice(WbPriceGood good) {
        BigDecimal price = BigDecimal.ZERO;
        BigDecimal discountPrice = BigDecimal.ZERO;
        if (good.sizes() != null && !good.sizes().isEmpty()) {
            var firstSize = good.sizes().get(0);
            price = BigDecimal.valueOf(firstSize.price());
            discountPrice = BigDecimal.valueOf(firstSize.discountedPrice());
        }
        return new NormalizedPriceItem(
                String.valueOf(good.nmID()),
                price,
                discountPrice,
                BigDecimal.valueOf(good.discount()),
                null,
                null,
                good.currencyIsoCode4217()
        );
    }

    public NormalizedStockItem normalizeStock(WbStockItem item) {
        return new NormalizedStockItem(
                String.valueOf(item.nmId()),
                String.valueOf(item.warehouseId()),
                item.quantity(),
                0
        );
    }

    public NormalizedOrderItem normalizeOrder(WbOrderItem item) {
        OffsetDateTime orderDate = WbTimestampParser.parseFlexible(item.date());
        String status = item.isCancel() ? "CANCELLED" : "CREATED";
        BigDecimal priceWithDisc = Objects.requireNonNullElse(item.priceWithDisc(), BigDecimal.ZERO);
        return new NormalizedOrderItem(
                item.srid(),
                item.supplierArticle(),
                1,
                priceWithDisc,
                priceWithDisc,
                "RUB",
                orderDate,
                status,
                "FBW",
                item.regionName()
        );
    }

    public NormalizedSaleItem normalizeSale(WbSaleItem item) {
        OffsetDateTime saleDate = WbTimestampParser.parseFlexible(item.date());
        BigDecimal saleAmount = Objects.requireNonNullElse(item.priceWithDisc(), BigDecimal.ZERO);
        BigDecimal commission = saleAmount.subtract(
                Objects.requireNonNullElse(item.forPay(), BigDecimal.ZERO));
        return new NormalizedSaleItem(
                item.saleID(),
                item.supplierArticle(),
                1,
                saleAmount,
                commission,
                "RUB",
                saleDate
        );
    }

    /**
     * DD-7: Sign conventions — debit fields multiplied by -1.
     * DD-8: Composite row model — single WB row maps to single NormalizedFinanceItem with 12 measures.
     * DD-9: Dual-format timestamp parsing.
     * DD-17: sale_dt fallback to rr_dt.
     *
     * <p>For WB_RETURN entries, revenue goes to refund_amount (negative = debit to seller)
     * and revenue_amount stays zero. For all other types, revenue populates revenue_amount.</p>
     */
    public NormalizedFinanceItem normalizeFinance(WbFinanceRow row) {
        OffsetDateTime entryDate = resolveEntryDate(row);
        FinanceEntryType entryType = FinanceEntryType.fromWbDocTypeName(row.docTypeName());

        boolean isReturn = entryType == FinanceEntryType.WB_RETURN;
        BigDecimal retailPrice = safe(row.retailPriceWithdiscRub());
        BigDecimal revenueAmount = isReturn ? BigDecimal.ZERO : retailPrice;
        BigDecimal refundAmount = isReturn ? retailPrice.negate() : BigDecimal.ZERO;

        BigDecimal marketingCost = negate(safe(row.cashbackAmount()))
                .add(negate(safe(row.sellerPromoDiscount())))
                .add(negate(safe(row.loyaltyDiscount())));

        String warehouseExternalId = row.ppvzOfficeId() != null
                ? String.valueOf(row.ppvzOfficeId())
                : null;

        return new NormalizedFinanceItem(
                String.valueOf(row.rrdId()),
                entryType,
                row.srid(),
                row.orderUid(),
                row.saName(),
                String.valueOf(row.nmId()),
                warehouseExternalId,
                revenueAmount,
                negate(safe(row.ppvzSalesCommission())),
                negate(safe(row.acquiringFee())),
                negate(safe(row.deliveryRub())).add(negate(safe(row.rebillLogisticCost()))),
                negate(safe(row.storageFee())),
                negate(safe(row.penalty())),
                negate(safe(row.acceptance())),
                marketingCost,
                negate(safe(row.deduction())),
                safe(row.additionalPayment()),
                refundAmount,
                safe(row.ppvzForPay()),
                row.currencyName(),
                entryDate
        );
    }

    public NormalizedWarehouse normalizeWarehouse(WbOffice office) {
        return new NormalizedWarehouse(
                String.valueOf(office.id()),
                office.name(),
                "WB",
                MARKETPLACE_WB
        );
    }

    public NormalizedReturnItem normalizeReturn(WbReturnItem item) {
        OffsetDateTime returnDate = WbTimestampParser.parseFlexible(item.orderDt());
        return new NormalizedReturnItem(
                item.srid(),
                null,
                1,
                BigDecimal.ZERO,
                item.returnType(),
                "RUB",
                returnDate,
                item.status()
        );
    }

    public NormalizedPromoCampaign normalizePromoCampaign(WbPromotionDto dto) {
        OffsetDateTime dateFrom = dto.startDateTime() != null
                ? WbTimestampParser.parseFlexible(dto.startDateTime()) : null;
        OffsetDateTime dateTo = dto.endDateTime() != null
                ? WbTimestampParser.parseFlexible(dto.endDateTime()) : null;

        String status = resolveWbPromoStatus(dateFrom, dateTo);
        String rawPayload = serializeToJson(dto);

        return new NormalizedPromoCampaign(
                String.valueOf(dto.id()),
                dto.name(),
                dto.type() != null ? dto.type() : "regular",
                status,
                dateFrom,
                dateTo,
                null,
                dto.description(),
                "DISCOUNT",
                dto.inAction(),
                rawPayload
        );
    }

    public NormalizedPromoProduct normalizePromoNomenclature(WbPromotionNomenclatureDto dto,
                                                            long promotionId) {
        String participationStatus = Boolean.TRUE.equals(dto.inAction())
                ? "PARTICIPATING" : "ELIGIBLE";
        BigDecimal requiredPrice = BigDecimal.valueOf(dto.effectivePromoPrice());
        BigDecimal currentPrice = dto.price() != null ? BigDecimal.valueOf(dto.price()) : null;

        return new NormalizedPromoProduct(
                String.valueOf(promotionId),
                dto.nmId() != null ? String.valueOf(dto.nmId()) : null,
                participationStatus,
                requiredPrice,
                currentPrice,
                null,
                null,
                null,
                null
        );
    }

    private String resolveWbPromoStatus(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
        OffsetDateTime now = OffsetDateTime.now();
        if (dateTo != null && dateTo.isBefore(now)) {
            return "ENDED";
        }
        if (dateFrom != null && dateFrom.isAfter(now)) {
            return "UPCOMING";
        }
        return "ACTIVE";
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize promo payload: {}", e.getMessage());
            return null;
        }
    }

    private OffsetDateTime resolveEntryDate(WbFinanceRow row) {
        String saleDt = row.saleDt();
        if (saleDt != null && !saleDt.isBlank()) {
            return WbTimestampParser.parseFlexible(saleDt);
        }
        return WbTimestampParser.parseFlexible(row.rrDt());
    }

    private static String extractFirstBarcode(WbCatalogCard card) {
        if (card.sizes() == null || card.sizes().isEmpty()) {
            return null;
        }
        var firstSize = card.sizes().get(0);
        if (firstSize.skus() == null || firstSize.skus().isEmpty()) {
            return null;
        }
        return firstSize.skus().get(0);
    }

    private static BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal negate(BigDecimal value) {
        return value.negate();
    }
}
