package io.datapulse.etl.adapter.wb;

import java.util.Map;

/**
 * Maps WB Advert API integer codes to human-readable strings
 * for {@code campaign_type} and {@code status} fields.
 *
 * <p>Source: advertising.md § Маппинг полей по маркетплейсам.</p>
 */
public final class WbCampaignTypeMapper {

  private WbCampaignTypeMapper() {
  }

  private static final Map<Integer, String> TYPES = Map.of(
      4, "CATALOG",
      5, "CARD",
      6, "SEARCH",
      7, "RECO",
      8, "AUTO",
      9, "SEARCH_PLUS_CATALOG"
  );

  private static final Map<Integer, String> STATUSES = Map.of(
      4, "ready",
      7, "active",
      8, "on_pause",
      9, "archived",
      11, "paused"
  );

  public static String mapType(int type) {
    return TYPES.getOrDefault(type, "UNKNOWN_" + type);
  }

  public static String mapStatus(int status) {
    return STATUSES.getOrDefault(status, "unknown_" + status);
  }
}
