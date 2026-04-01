package io.datapulse.etl.adapter.ozon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.adapter.ozon.dto.OzonActionDto;
import io.datapulse.etl.adapter.ozon.dto.OzonActionProductDto;
import io.datapulse.etl.adapter.ozon.dto.OzonAttributeResponse;
import io.datapulse.etl.adapter.ozon.dto.OzonCategoryTreeResponse;
import io.datapulse.etl.adapter.ozon.dto.OzonFboPosting;
import io.datapulse.etl.adapter.ozon.dto.OzonFbsPosting;
import io.datapulse.etl.adapter.ozon.dto.OzonPriceItem;
import io.datapulse.etl.adapter.ozon.dto.OzonProductInfo;
import io.datapulse.etl.adapter.ozon.dto.OzonReturnItem;
import io.datapulse.etl.adapter.ozon.dto.OzonStockItem;
import io.datapulse.etl.adapter.util.OzonTimestampParser;
import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.domain.normalized.NormalizedCategory;
import io.datapulse.etl.domain.normalized.NormalizedOrderItem;
import io.datapulse.etl.domain.normalized.NormalizedPriceItem;
import io.datapulse.etl.domain.normalized.NormalizedPromoCampaign;
import io.datapulse.etl.domain.normalized.NormalizedPromoProduct;
import io.datapulse.etl.domain.normalized.NormalizedReturnItem;
import io.datapulse.etl.domain.normalized.NormalizedSaleItem;
import io.datapulse.etl.domain.normalized.NormalizedStockItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stateless normalizer: transforms Ozon provider DTOs into marketplace-agnostic normalized records.
 * Handles products, prices, stocks, orders, returns, categories.
 * Finance normalization → {@link OzonFinanceNormalizer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonNormalizer {

    private static final long BRAND_ATTRIBUTE_ID = 85;
    private static final String MARKETPLACE_OZON = "OZON";

    private final ObjectMapper objectMapper;

    public NormalizedCatalogItem normalizeProductInfo(OzonProductInfo info) {
        String marketplaceSku = String.valueOf(info.id());
        String barcode = info.barcodes() != null && !info.barcodes().isEmpty()
                ? info.barcodes().get(0)
                : info.barcode();
        String status = resolveProductStatus(info);

        return new NormalizedCatalogItem(
                info.offerId(),
                marketplaceSku,
                null,
                info.name(),
                null,
                null,
                barcode,
                status
        );
    }

    /**
     * DD-8: Brand is attribute_id=85 in the attributes API response.
     */
    public String extractBrand(OzonAttributeResponse.OzonAttributeResult attrResult) {
        if (attrResult.attributes() == null) {
            return null;
        }
        return attrResult.attributes().stream()
                .filter(a -> a.attributeId() == BRAND_ATTRIBUTE_ID)
                .flatMap(a -> a.values() != null ? a.values().stream() : Stream.empty())
                .map(OzonAttributeResponse.OzonAttributeValue::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * DD-1: price is a NUMBER (STRING in API, parse to BigDecimal).
     */
    public NormalizedPriceItem normalizePrice(OzonPriceItem item) {
        BigDecimal price = parseBigDecimal(item.price() != null ? item.price().price() : null);
        BigDecimal marketingSellerPrice = parseBigDecimal(
                item.price() != null ? item.price().marketingSellerPrice() : null);
        BigDecimal minPrice = parseBigDecimal(
                item.price() != null ? item.price().minPrice() : null);
        String currency = item.price() != null ? item.price().currencyCode() : null;

        return new NormalizedPriceItem(
                String.valueOf(item.productId()),
                price,
                marketingSellerPrice,
                null,
                minPrice,
                null,
                currency
        );
    }

    /**
     * DD-2: warehouse resolved from warehouse_ids or fallback to type.
     */
    public List<NormalizedStockItem> normalizeStocks(OzonStockItem item) {
        if (item.stocks() == null) {
            return List.of();
        }
        List<NormalizedStockItem> result = new ArrayList<>();
        for (var entry : item.stocks()) {
            String warehouseId = resolveWarehouseId(entry);
            result.add(new NormalizedStockItem(
                    String.valueOf(item.productId()),
                    warehouseId,
                    entry.present(),
                    entry.reserved()
            ));
        }
        return result;
    }

    public NormalizedOrderItem normalizeFboPosting(OzonFboPosting posting,
                                                   OzonFboPosting.OzonPostingProduct product) {
        OffsetDateTime orderDate = OzonTimestampParser.parseIso8601(posting.createdAt());
        if (orderDate == null) {
            orderDate = OzonTimestampParser.parseIso8601(posting.inProcessAt());
        }
        if (orderDate == null) {
            orderDate = OffsetDateTime.now();
            log.warn("FBO posting has no date, using now(): postingNumber={}", posting.postingNumber());
        }
        BigDecimal pricePerUnit = parseBigDecimal(product.price());
        BigDecimal totalAmount = pricePerUnit.multiply(BigDecimal.valueOf(product.quantity()));
        String region = posting.analyticsData() != null ? posting.analyticsData().region() : null;

        return new NormalizedOrderItem(
                posting.postingNumber(),
                product.offerId(),
                product.quantity(),
                pricePerUnit,
                totalAmount,
                product.currencyCode(),
                orderDate,
                posting.status(),
                "FBO",
                region
        );
    }

    public NormalizedOrderItem normalizeFbsPosting(OzonFbsPosting posting,
                                                   OzonFboPosting.OzonPostingProduct product) {
        OffsetDateTime orderDate = OzonTimestampParser.parseIso8601(posting.createdAt());
        if (orderDate == null) {
            orderDate = OzonTimestampParser.parseIso8601(posting.inProcessAt());
        }
        if (orderDate == null) {
            orderDate = OffsetDateTime.now();
            log.warn("FBS posting has no date, using now(): postingNumber={}", posting.postingNumber());
        }
        BigDecimal pricePerUnit = parseBigDecimal(product.price());
        BigDecimal totalAmount = pricePerUnit.multiply(BigDecimal.valueOf(product.quantity()));
        String region = posting.analyticsData() != null ? posting.analyticsData().region() : null;

        return new NormalizedOrderItem(
                posting.postingNumber(),
                product.offerId(),
                product.quantity(),
                pricePerUnit,
                totalAmount,
                product.currencyCode(),
                orderDate,
                posting.status(),
                "FBS",
                region
        );
    }

    private static final Set<String> DELIVERED_STATUSES = Set.of(
            "delivered", "client_arbitration", "arbitration");

    public boolean isDeliveredPosting(String status) {
        return status != null && DELIVERED_STATUSES.contains(status.toLowerCase());
    }

    public NormalizedSaleItem normalizeFboSale(OzonFboPosting posting,
                                               OzonFboPosting.OzonPostingProduct product) {
        OffsetDateTime saleDate = OzonTimestampParser.parseIso8601(posting.createdAt());
        if (saleDate == null) {
            saleDate = OzonTimestampParser.parseIso8601(posting.inProcessAt());
        }
        if (saleDate == null) {
            saleDate = OffsetDateTime.now();
            log.warn("FBO posting has no date for sale, using now(): postingNumber={}",
                    posting.postingNumber());
        }
        BigDecimal pricePerUnit = parseBigDecimal(product.price());
        BigDecimal saleAmount = pricePerUnit.multiply(BigDecimal.valueOf(product.quantity()));

        return new NormalizedSaleItem(
                posting.postingNumber() + "-" + product.sku(),
                product.offerId(),
                product.quantity(),
                saleAmount,
                null,
                product.currencyCode(),
                saleDate
        );
    }

    public NormalizedSaleItem normalizeFbsSale(OzonFbsPosting posting,
                                               OzonFboPosting.OzonPostingProduct product) {
        OffsetDateTime saleDate = OzonTimestampParser.parseIso8601(posting.createdAt());
        if (saleDate == null) {
            saleDate = OzonTimestampParser.parseIso8601(posting.inProcessAt());
        }
        if (saleDate == null) {
            saleDate = OffsetDateTime.now();
            log.warn("FBS posting has no date for sale, using now(): postingNumber={}",
                    posting.postingNumber());
        }
        BigDecimal pricePerUnit = parseBigDecimal(product.price());
        BigDecimal saleAmount = pricePerUnit.multiply(BigDecimal.valueOf(product.quantity()));

        return new NormalizedSaleItem(
                posting.postingNumber() + "-" + product.sku(),
                product.offerId(),
                product.quantity(),
                saleAmount,
                null,
                product.currencyCode(),
                saleDate
        );
    }

    public NormalizedReturnItem normalizeReturn(OzonReturnItem item) {
        OffsetDateTime returnDate = OzonTimestampParser.parseIso8601(item.returnDate());
        BigDecimal returnAmount = BigDecimal.ZERO;
        String currency = null;
        String sellerSku = null;
        int quantity = 1;

        if (item.product() != null) {
            sellerSku = item.product().offerId();
            quantity = item.product().quantity();
            if (item.product().price() != null) {
                returnAmount = parseBigDecimal(item.product().price().price());
                currency = item.product().price().currencyCode();
            }
        }

        return new NormalizedReturnItem(
                String.valueOf(item.id()),
                sellerSku,
                quantity,
                returnAmount,
                item.returnReasonName(),
                currency,
                returnDate,
                item.status()
        );
    }

    /**
     * Recursive flatten of category tree.
     */
    public List<NormalizedCategory> flattenCategoryTree(
            List<OzonCategoryTreeResponse.OzonCategoryNode> nodes) {
        List<NormalizedCategory> result = new ArrayList<>();
        flattenRecursive(nodes, null, result);
        return result;
    }

    private void flattenRecursive(List<OzonCategoryTreeResponse.OzonCategoryNode> nodes,
                                  String parentId,
                                  List<NormalizedCategory> result) {
        if (nodes == null) {
            return;
        }
        for (var node : nodes) {
            String id = String.valueOf(node.descriptionCategoryId());
            result.add(new NormalizedCategory(
                    id,
                    node.categoryName(),
                    parentId,
                    MARKETPLACE_OZON
            ));
            flattenRecursive(node.children(), id, result);
        }
    }

    public NormalizedPromoCampaign normalizeAction(OzonActionDto dto) {
        OffsetDateTime dateFrom = dto.dateStart() != null
                ? OzonTimestampParser.parseIso8601(dto.dateStart()) : null;
        OffsetDateTime dateTo = dto.dateEnd() != null
                ? OzonTimestampParser.parseIso8601(dto.dateEnd()) : null;
        OffsetDateTime freezeAt = dto.freezeDate() != null && !dto.freezeDate().isBlank()
                ? OzonTimestampParser.parseIso8601(dto.freezeDate()) : null;

        String status = resolveOzonPromoStatus(dateFrom, dateTo);
        String mechanic = dto.discountType() != null ? dto.discountType() : "DISCOUNT";
        String rawPayload = serializeToJson(dto);

        return new NormalizedPromoCampaign(
                String.valueOf(dto.id()),
                dto.title(),
                dto.actionType() != null ? dto.actionType() : "unknown",
                status,
                dateFrom,
                dateTo,
                freezeAt,
                null,
                dto.description(),
                mechanic,
                dto.isParticipating(),
                rawPayload
        );
    }

    public NormalizedPromoProduct normalizeActionProduct(OzonActionProductDto dto,
                                                        long actionId,
                                                        boolean isParticipating) {
        String participationStatus = isParticipating ? "PARTICIPATING" : "ELIGIBLE";
        BigDecimal requiredPrice = BigDecimal.valueOf(dto.actionPrice());
        BigDecimal currentPrice = BigDecimal.valueOf(dto.price());
        BigDecimal maxPromoPrice = BigDecimal.valueOf(dto.maxActionPrice());

        return new NormalizedPromoProduct(
                String.valueOf(actionId),
                String.valueOf(dto.productId()),
                participationStatus,
                requiredPrice,
                currentPrice,
                maxPromoPrice,
                null,
                dto.addMode(),
                dto.minStock(),
                dto.stock()
        );
    }

    private String resolveOzonPromoStatus(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
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

    private static String resolveProductStatus(OzonProductInfo info) {
        if (info.isArchived() || info.isAutoarchived()) {
            return "ARCHIVED";
        }
        if (info.visibility() != null && info.visibility().visible()) {
            return "ACTIVE";
        }
        return "INACTIVE";
    }

    private static String resolveWarehouseId(OzonStockItem.OzonStockEntry entry) {
        if (entry.warehouseIds() != null && !entry.warehouseIds().isEmpty()) {
            return String.valueOf(entry.warehouseIds().get(0));
        }
        return entry.type() != null ? entry.type() : "UNKNOWN";
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from Ozon value: value={}", value);
            return BigDecimal.ZERO;
        }
    }

}
