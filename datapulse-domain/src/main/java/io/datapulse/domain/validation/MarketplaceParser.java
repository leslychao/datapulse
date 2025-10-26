package io.datapulse.domain.validation;

import io.datapulse.domain.MarketplaceType;
import java.util.Map;
import java.util.Optional;

public final class MarketplaceParser {

  private MarketplaceParser() {
  }

  private static final Map<String, MarketplaceType> ALIASES = Map.ofEntries(
      Map.entry("wb", MarketplaceType.WILDBERRIES),
      Map.entry("wildberries", MarketplaceType.WILDBERRIES),
      Map.entry("вб", MarketplaceType.WILDBERRIES),

      Map.entry("ozon", MarketplaceType.OZON),
      Map.entry("озон", MarketplaceType.OZON)
  );

  public static Optional<MarketplaceType> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String key = normalizeLower(text);

    for (MarketplaceType mt : MarketplaceType.values()) {
      if (mt.name().equalsIgnoreCase(key)) {
        return Optional.of(mt);
      }
    }
    return Optional.ofNullable(ALIASES.get(key));
  }

  public static String allowedList() {
    return "WILDBERRIES, OZON, а также алиасы: " + String.join(", ", ALIASES.keySet());
  }

  private static String normalizeLower(String str) {
    return str == null ? "" : str.trim().toLowerCase();
  }
}
