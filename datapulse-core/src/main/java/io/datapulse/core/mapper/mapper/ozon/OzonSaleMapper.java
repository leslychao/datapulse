package io.datapulse.core.mapper.mapper.ozon;

import io.datapulse.domain.dto.SaleDto;
import io.datapulse.domain.dto.raw.ozon.OzonAnalyticsRowRaw;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring", imports = {BigDecimal.class, LocalDate.class})
public abstract class OzonSaleMapper {

  protected static final String DIM_SKU = "sku";
  protected static final String DIM_PRODUCT = "product";
  protected static final int METRIC_REVENUE_INDEX = 0;
  protected static final int METRIC_ORDERS_INDEX = 1;

  @Mappings({
      @Mapping(target = "sku", expression = "java(extractSku(raw))"),
      @Mapping(target = "postingNumber", ignore = true),
      @Mapping(target = "offerId", ignore = true),
      @Mapping(target = "fulfillment", ignore = true),
      @Mapping(target = "status", constant = "aggregated"),
      @Mapping(target = "isCancelled", constant = "false"),
      @Mapping(target = "isReturn", constant = "false"),
      @Mapping(target = "eventDate", expression = "java(LocalDate.now())"),
      @Mapping(target = "createdAt", ignore = true),
      @Mapping(target = "processedAt", ignore = true),
      @Mapping(target = "quantity", expression = "java(extractOrders(raw))"),
      @Mapping(target = "priceOriginal", ignore = true),
      @Mapping(target = "priceFinal", ignore = true),
      @Mapping(target = "revenue", expression = "java(extractRevenue(raw))"),
      @Mapping(target = "cost", ignore = true),
      @Mapping(target = "commissionAmount", ignore = true),
      @Mapping(target = "deliveryAmount", ignore = true),
      @Mapping(target = "storageFeeAmount", ignore = true),
      @Mapping(target = "penaltyAmount", ignore = true),
      @Mapping(target = "marketingAmount", ignore = true)
  })
  public abstract SaleDto toDto(OzonAnalyticsRowRaw raw);

  protected String extractSku(OzonAnalyticsRowRaw raw) {
    if (raw == null || raw.dimensions() == null || raw.dimensions().isEmpty()) {
      return null;
    }
    return raw.dimensions().stream()
        .filter(dimension -> {
          String id = dimension.id();
          return (DIM_SKU.equalsIgnoreCase(id) || DIM_PRODUCT.equalsIgnoreCase(id));
        })
        .map(OzonAnalyticsRowRaw.Dimension::name)
        .findFirst()
        .orElse(null);
  }

  protected Integer extractOrders(OzonAnalyticsRowRaw raw) {
    Double orders = safeGetMetric(raw, METRIC_ORDERS_INDEX);
    return orders == null ? null : orders.intValue();
  }

  protected BigDecimal extractRevenue(OzonAnalyticsRowRaw raw) {
    Double revenue = safeGetMetric(raw, METRIC_REVENUE_INDEX);
    return revenue == null ? null : BigDecimal.valueOf(revenue);
  }

  private Double safeGetMetric(OzonAnalyticsRowRaw raw, int index) {
    if (raw == null) {
      return null;
    }

    List<Double> metrics = raw.metrics();
    if (metrics == null || metrics.size() <= index) {
      return null;
    }

    Double value = metrics.get(index);
    if (value == null || value.isNaN() || value.isInfinite()) {
      return null;
    }
    return value;
  }
}
