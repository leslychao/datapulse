package io.datapulse.etl;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_UNKNOWN;

import io.datapulse.domain.exception.AppException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public enum MarketplaceEvent {

  WAREHOUSE_DICT,
  CATEGORY_DICT,
  TARIFF_DICT,
  PRODUCT_DICT,
  SALES_FACT,
  SHIPMENT_FACT,
  SUPPLY_ACCEPTED_FACT,
  INVENTORY_FACT,
  FACT_FINANCE;

  public String tag() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static MarketplaceEvent fromString(String raw) {
    if (StringUtils.isBlank(raw)) {
      throw new IllegalArgumentException("MarketplaceEvent is null or blank");
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      return Arrays.stream(values())
          .filter(e -> e.name().equalsIgnoreCase(raw.trim()))
          .findFirst()
          .orElseThrow(() -> new AppException(ETL_EVENT_UNKNOWN, raw));
    }
  }

  public Set<MarketplaceEvent> dependencies() {
    return switch (this) {
      case WAREHOUSE_DICT, CATEGORY_DICT -> Set.of();
      case TARIFF_DICT -> Set.of(CATEGORY_DICT);
      case PRODUCT_DICT -> Set.of(CATEGORY_DICT, WAREHOUSE_DICT, TARIFF_DICT);
      case SALES_FACT, SHIPMENT_FACT, SUPPLY_ACCEPTED_FACT -> Set.of(PRODUCT_DICT);
      case INVENTORY_FACT -> Set.of(PRODUCT_DICT, WAREHOUSE_DICT);
      case FACT_FINANCE -> Set.of(SALES_FACT);
    };
  }
}
