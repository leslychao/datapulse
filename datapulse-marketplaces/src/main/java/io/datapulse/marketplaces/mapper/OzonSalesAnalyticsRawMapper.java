package io.datapulse.marketplaces.mapper;

import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.marketplaces.adapter.OzonAnalyticsSchema;
import io.datapulse.marketplaces.dto.normalized.OzonSalesAnalyticsRawDto;
import io.datapulse.marketplaces.dto.normalized.OzonSalesAnalyticsRawDto.MetricEntry;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw.OzonAnalyticsApiDimensionRaw;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(
    componentModel = "spring",
    config = BaseMapperConfig.class,
    builder = @Builder
)
public interface OzonSalesAnalyticsRawMapper {

  @Mapping(target = "productId", ignore = true)
  @Mapping(target = "offerId", ignore = true)
  @Mapping(target = "warehouseId", ignore = true)
  @Mapping(target = "day", ignore = true)
  @Mapping(target = "metrics", ignore = true)
  OzonSalesAnalyticsRawDto toDto(OzonAnalyticsApiRaw raw);

  @AfterMapping
  default void normalize(
      OzonAnalyticsApiRaw raw,
      @MappingTarget OzonSalesAnalyticsRawDto.OzonSalesAnalyticsRawDtoBuilder target
  ) {
    String productId = extract(raw.dimensions(), "product_id");
    String offerId = extract(raw.dimensions(), "offer_id");
    String warehouseId = extract(raw.dimensions(), "warehouse_id");
    String day = extract(raw.dimensions(), "day");

    LocalDate parsedDay = parseDaySafely(day);
    List<MetricEntry> metricEntries = mapMetrics(raw.metrics());

    target
        .productId(productId)
        .offerId(offerId)
        .warehouseId(warehouseId)
        .day(parsedDay)
        .metrics(metricEntries);
  }

  private String extract(List<OzonAnalyticsApiDimensionRaw> dims, String id) {
    if (dims == null || dims.isEmpty()) {
      return null;
    }
    return dims.stream()
        .filter(d -> id.equals(d.id()))
        .findFirst()
        .map(OzonAnalyticsApiDimensionRaw::value)
        .orElse(null);
  }

  private LocalDate parseDaySafely(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private List<MetricEntry> mapMetrics(List<BigDecimal> metrics) {
    if (metrics == null || metrics.isEmpty()) {
      return Collections.emptyList();
    }

    List<MetricEntry> entries = new ArrayList<>();

    int limit = Math.min(
        metrics.size(),
        OzonAnalyticsSchema.SALES_FACT_METRICS.size()
    );

    for (int index = 0; index < limit; index++) {
      String id = OzonAnalyticsSchema.SALES_FACT_METRICS.get(index);
      BigDecimal value = metrics.get(index);
      entries.add(new MetricEntry(id, value));
    }

    return entries;
  }
}
