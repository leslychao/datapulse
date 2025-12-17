package io.datapulse.etl;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_UNKNOWN;

import io.datapulse.domain.exception.AppException;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public enum MarketplaceEvent {

  WAREHOUSE_DICT, CATEGORY_DICT, COMMISSION_DICT, PRODUCT_DICT, SALES_FACT;

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
}
