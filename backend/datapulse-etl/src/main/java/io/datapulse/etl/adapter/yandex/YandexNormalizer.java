package io.datapulse.etl.adapter.yandex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.datapulse.etl.adapter.yandex.dto.YandexOffer;
import io.datapulse.etl.adapter.yandex.dto.YandexOfferMapping;
import io.datapulse.etl.adapter.yandex.dto.YandexOrder;
import io.datapulse.etl.adapter.yandex.dto.YandexOrderItem;
import io.datapulse.etl.adapter.yandex.dto.YandexOrderPrice;
import io.datapulse.etl.adapter.yandex.dto.YandexPromo;
import io.datapulse.etl.adapter.yandex.dto.YandexRealizationReportRow;
import io.datapulse.etl.adapter.yandex.dto.YandexReturn;
import io.datapulse.etl.adapter.yandex.dto.YandexReturnItem;
import io.datapulse.etl.adapter.yandex.dto.YandexServicesReportRow;
import io.datapulse.etl.adapter.yandex.dto.YandexStockEntry;
import io.datapulse.etl.adapter.yandex.dto.YandexStockOffer;
import io.datapulse.etl.adapter.yandex.dto.YandexStockWarehouse;
import io.datapulse.etl.adapter.yandex.dto.YandexWarehouse;
import io.datapulse.etl.domain.FinanceEntryType;
import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.domain.normalized.NormalizedOrderItem;
import io.datapulse.etl.domain.normalized.NormalizedPriceItem;
import io.datapulse.etl.domain.normalized.NormalizedPromoCampaign;
import io.datapulse.etl.domain.normalized.NormalizedReturnItem;
import io.datapulse.etl.domain.normalized.NormalizedStockItem;
import io.datapulse.etl.domain.normalized.NormalizedWarehouse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stateless normalizer: transforms Yandex Market provider DTOs into marketplace-agnostic
 * normalized records. Handles catalog (offer-mappings), prices, stocks, orders, returns.
 */
@Slf4j
@Service
public class YandexNormalizer {

  private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String BUYER_PRICE_TYPE = "BUYER";

  private static final Set<String> ACTIVE_CARD_STATUSES = Set.of(
      "HAS_CARD_CAN_UPDATE",
      "HAS_CARD_CAN_NOT_UPDATE",
      "HAS_CARD_CAN_UPDATE_PROCESSING");

  public List<NormalizedCatalogItem> normalizeCatalog(List<YandexOfferMapping> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream()
        .map(this::normalizeCatalogItem)
        .toList();
  }

  public List<NormalizedPriceItem> normalizePrices(List<YandexOfferMapping> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream()
        .map(this::normalizePriceItem)
        .toList();
  }

  public List<NormalizedStockItem> normalizeStocks(List<YandexStockWarehouse> warehouses) {
    if (warehouses == null) {
      return List.of();
    }
    List<NormalizedStockItem> result = new ArrayList<>();
    for (var warehouse : warehouses) {
      if (warehouse.offers() == null) {
        continue;
      }
      String warehouseId = String.valueOf(warehouse.warehouseId());
      for (var offer : warehouse.offers()) {
        result.add(normalizeStockOffer(offer, warehouseId));
      }
    }
    return result;
  }

  public List<NormalizedOrderItem> normalizeOrders(List<YandexOrder> orders) {
    if (orders == null) {
      return List.of();
    }
    List<NormalizedOrderItem> result = new ArrayList<>();
    for (var order : orders) {
      if (order.items() == null) {
        continue;
      }
      for (var item : order.items()) {
        result.add(normalizeOrderItem(order, item));
      }
    }
    return result;
  }

  public NormalizedWarehouse normalizeWarehouse(YandexWarehouse warehouse, String warehouseType) {
    return new NormalizedWarehouse(
        String.valueOf(warehouse.id()),
        warehouse.name(),
        warehouseType,
        "YANDEX"
    );
  }

  public NormalizedPromoCampaign normalizePromo(YandexPromo promo) {
    return new NormalizedPromoCampaign(
        promo.promoId(),
        promo.name(),
        promo.mechanicsType(),
        promo.status(),
        parseDateOnly(promo.startDate()),
        parseDateOnly(promo.endDate()),
        null,
        null,
        null,
        promo.mechanicsType(),
        "ACTIVE".equalsIgnoreCase(promo.status()),
        null
    );
  }

  public List<NormalizedReturnItem> normalizeReturns(List<YandexReturn> returns) {
    if (returns == null) {
      return List.of();
    }
    List<NormalizedReturnItem> result = new ArrayList<>();
    for (var ret : returns) {
      if (ret.items() == null) {
        continue;
      }
      for (var item : ret.items()) {
        result.add(normalizeReturnItem(ret, item));
      }
    }
    return result;
  }

  private NormalizedCatalogItem normalizeCatalogItem(YandexOfferMapping mapping) {
    YandexOffer offer = mapping.offer();
    String marketplaceSku = mapping.mapping() != null && mapping.mapping().marketSku() != null
        ? String.valueOf(mapping.mapping().marketSku())
        : null;
    String category = mapping.mapping() != null
        ? mapping.mapping().marketCategoryName()
        : null;
    String barcode = extractFirstBarcode(offer);
    String status = resolveOfferStatus(offer);

    return new NormalizedCatalogItem(
        offer.offerId(),
        marketplaceSku,
        null,
        offer.name(),
        offer.vendor(),
        category,
        barcode,
        status
    );
  }

  private NormalizedPriceItem normalizePriceItem(YandexOfferMapping mapping) {
    YandexOffer offer = mapping.offer();
    BigDecimal price = offer.basicPrice() != null ? offer.basicPrice().value() : null;
    String currency = normalizeCurrency(
        offer.basicPrice() != null ? offer.basicPrice().currencyId() : null);
    BigDecimal purchasePrice = offer.purchasePrice() != null
        ? offer.purchasePrice().value()
        : null;

    return new NormalizedPriceItem(
        offer.offerId(),
        price,
        null,
        null,
        null,
        purchasePrice,
        currency
    );
  }

  private NormalizedStockItem normalizeStockOffer(YandexStockOffer offer, String warehouseId) {
    int available = 0;
    int reserved = 0;
    int total = 0;

    if (offer.stocks() != null) {
      for (YandexStockEntry entry : offer.stocks()) {
        if (entry.type() == null) {
          continue;
        }
        switch (entry.type()) {
          case "AVAILABLE" -> available = entry.count();
          case "FREEZE" -> reserved = entry.count();
          case "FIT" -> total = entry.count();
          default -> { }
        }
      }
    }

    if (available == 0 && total > 0) {
      available = total - reserved;
    }

    return new NormalizedStockItem(
        offer.offerId(),
        warehouseId,
        available,
        reserved
    );
  }

  private NormalizedOrderItem normalizeOrderItem(YandexOrder order, YandexOrderItem item) {
    BigDecimal pricePerUnit = BigDecimal.ZERO;
    BigDecimal totalAmount = BigDecimal.ZERO;

    if (item.prices() != null) {
      for (YandexOrderPrice price : item.prices()) {
        if (BUYER_PRICE_TYPE.equals(price.type())) {
          pricePerUnit = safe(price.costPerItem());
          totalAmount = safe(price.total());
          break;
        }
      }
    }

    OffsetDateTime orderDate = parseDateOnly(order.creationDate());
    String region = order.delivery() != null && order.delivery().region() != null
        ? order.delivery().region().name()
        : null;

    return new NormalizedOrderItem(
        String.valueOf(order.id()),
        item.offerId(),
        item.count(),
        pricePerUnit,
        totalAmount,
        "RUB",
        orderDate,
        order.status(),
        order.programType(),
        region
    );
  }

  private NormalizedReturnItem normalizeReturnItem(YandexReturn ret, YandexReturnItem item) {
    OffsetDateTime returnDate = parseDateOnly(ret.creationDate());
    String reason = item.returnReason() != null ? item.returnReason().type() : null;

    return new NormalizedReturnItem(
        String.valueOf(ret.id()),
        item.shopSku(),
        item.count(),
        BigDecimal.ZERO,
        reason,
        "RUB",
        returnDate,
        ret.returnStatus(),
        null
    );
  }

  private static String resolveOfferStatus(YandexOffer offer) {
    if (offer.archived()) {
      return "ARCHIVED";
    }
    if (offer.cardStatus() != null && ACTIVE_CARD_STATUSES.contains(offer.cardStatus())) {
      return "ACTIVE";
    }
    if (offer.cardStatus() != null && offer.cardStatus().startsWith("NO_CARD_")) {
      return "BLOCKED";
    }
    return "ACTIVE";
  }

  private static String extractFirstBarcode(YandexOffer offer) {
    if (offer.barcodes() == null || offer.barcodes().isEmpty()) {
      return null;
    }
    return offer.barcodes().get(0);
  }

  /**
   * Normalizes a services report row into a canonical finance item.
   *
   * <p>Sign convention (assumed, DD-26): {@code totalAmount} represents cost to seller,
   * stored as negative (debit) in canonical. If real data shows different convention,
   * negate here.</p>
   */
  public NormalizedFinanceItem normalizeServiceCharge(YandexServicesReportRow row) {
    FinanceEntryType entryType = FinanceEntryType.fromYandexServiceName(row.serviceName());
    BigDecimal amount = safe(row.totalAmount()).negate();

    String externalId = "ym-svc-%s-%s-%s".formatted(
        row.orderId() != null ? row.orderId() : "none",
        row.shopSku() != null ? row.shopSku() : "none",
        row.serviceName() != null ? row.serviceName().hashCode() : "0");

    OffsetDateTime entryDate = parseDateTime(row.serviceDateTime());
    if (entryDate == null) {
      entryDate = parseDateTime(row.orderCreationDateTime());
    }
    if (entryDate == null) {
      entryDate = parseDateOnly(row.actDate());
    }

    return new NormalizedFinanceItem(
        externalId,
        entryType,
        null,
        row.orderId() != null ? row.orderId().toString() : null,
        null,
        row.shopSku(),
        null,
        row.placementModel(),
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.REVENUE
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.MARKETPLACE_COMMISSION
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.ACQUIRING
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.LOGISTICS
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.STORAGE
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.PENALTIES
            ? amount : BigDecimal.ZERO,
        BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.MARKETING
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.OTHER
            ? amount : BigDecimal.ZERO,
        entryType.primaryMeasure() == FinanceEntryType.MeasureColumn.COMPENSATION
            ? amount : BigDecimal.ZERO,
        BigDecimal.ZERO,
        amount,
        "RUB",
        entryDate
    );
  }

  /**
   * Normalizes a realization report row into a canonical revenue entry.
   *
   * <p>Revenue = {@code priceWithVatAndNoDiscount × transferredToDeliveryCount}.</p>
   */
  public NormalizedFinanceItem normalizeRealization(YandexRealizationReportRow row) {
    BigDecimal unitPrice = safe(row.priceWithVatAndNoDiscount());
    int quantity = row.transferredToDeliveryCount() != null
        ? row.transferredToDeliveryCount() : 0;
    BigDecimal revenue = unitPrice.multiply(BigDecimal.valueOf(quantity));

    String externalId = "ym-rlz-%s-%s".formatted(
        row.orderId() != null ? row.orderId() : "none",
        row.shopSku() != null ? row.shopSku() : "none");

    OffsetDateTime entryDate = parseDateOnly(row.deliveryDate());
    if (entryDate == null) {
      entryDate = parseDateOnly(row.orderCreationDate());
    }

    return new NormalizedFinanceItem(
        externalId,
        FinanceEntryType.YANDEX_SALE,
        null,
        row.orderId() != null ? row.orderId().toString() : null,
        row.yourSku(),
        row.shopSku(),
        null,
        row.placementModel(),
        revenue,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        revenue,
        "RUB",
        entryDate
    );
  }

  private static OffsetDateTime parseDateTime(String dateTimeStr) {
    if (dateTimeStr == null || dateTimeStr.isBlank()) {
      return null;
    }
    try {
      if (dateTimeStr.length() <= 10) {
        return parseDateOnly(dateTimeStr);
      }
      return OffsetDateTime.parse(dateTimeStr);
    } catch (Exception e) {
      try {
        return LocalDate.parse(dateTimeStr.substring(0, 10), DATE_ONLY)
            .atStartOfDay().atOffset(ZoneOffset.UTC);
      } catch (Exception ex) {
        log.warn("Failed to parse Yandex datetime: value={}", dateTimeStr);
        return null;
      }
    }
  }

  /**
   * Yandex uses "RUR" as currency code — normalize to standard "RUB".
   */
  private static String normalizeCurrency(String currencyId) {
    if (currencyId == null) {
      return "RUB";
    }
    return "RUR".equals(currencyId) ? "RUB" : currencyId;
  }

  private static OffsetDateTime parseDateOnly(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(dateStr, DATE_ONLY);
      return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    } catch (Exception e) {
      log.warn("Failed to parse Yandex date: value={}", dateStr);
      return null;
    }
  }

  private static BigDecimal safe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
