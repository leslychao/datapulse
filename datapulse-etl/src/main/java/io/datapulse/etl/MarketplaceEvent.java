package io.datapulse.etl;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_UNKNOWN;

import io.datapulse.domain.exception.AppException;
import java.util.Arrays;
import java.util.Collections;
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
  FACT_LOGISTICS_COSTS,
  FACT_FINANCE,
  FACT_COMMISSION;

  public String tag() {
    return name().toLowerCase();
  }

  public static MarketplaceEvent fromString(String raw) {
    if (StringUtils.isBlank(raw)) {
      throw new IllegalArgumentException("MarketplaceEvent is null or blank");
    }
    String norm = raw.trim().toLowerCase();
    return Arrays.stream(values())
        .filter(e -> e.name().equalsIgnoreCase(norm))
        .findFirst()
        .orElseThrow(() -> new AppException(ETL_EVENT_UNKNOWN, raw));
  }

  public Set<MarketplaceEvent> requiredEvents() {
    return switch (this) {
      case WAREHOUSE_DICT, CATEGORY_DICT, TARIFF_DICT, PRODUCT_DICT -> Collections.emptySet();

      case SALES_FACT,
          FACT_FINANCE,
          SHIPMENT_FACT,
          SUPPLY_ACCEPTED_FACT,
          INVENTORY_FACT,
          FACT_LOGISTICS_COSTS,
          FACT_COMMISSION -> Set.of(PRODUCT_DICT);
    };
  }
}
