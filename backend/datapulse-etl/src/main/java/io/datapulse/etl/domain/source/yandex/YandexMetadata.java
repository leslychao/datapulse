package io.datapulse.etl.domain.source.yandex;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extracts Yandex-specific fields from {@code marketplace_connection.metadata} JSONB.
 * Metadata is populated by {@code YandexHealthProbe} during connection validation.
 *
 * <p>Expected JSON structure:
 * <pre>{@code
 * {
 *   "businessId": 12345,
 *   "campaigns": [
 *     { "campaignId": 111, "placementType": "FBY" },
 *     { "campaignId": 222, "placementType": "FBS" }
 *   ]
 * }
 * }</pre>
 */
public final class YandexMetadata {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final long businessId;
  private final List<Long> campaignIds;

  private YandexMetadata(long businessId, List<Long> campaignIds) {
    this.businessId = businessId;
    this.campaignIds = campaignIds;
  }

  public long businessId() {
    return businessId;
  }

  public List<Long> campaignIds() {
    return campaignIds;
  }

  /**
   * Parses Yandex metadata from raw JSON string.
   *
   * @throws IllegalStateException if metadata is null/empty or missing required fields
   */
  public static YandexMetadata parse(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) {
      throw new IllegalStateException(
          "Yandex connection metadata is empty — health probe may not have run");
    }

    try {
      JsonNode root = MAPPER.readTree(metadataJson);

      JsonNode businessNode = root.get("businessId");
      if (businessNode == null || businessNode.isNull()) {
        throw new IllegalStateException(
            "Yandex connection metadata missing businessId");
      }
      long businessId = businessNode.asLong();

      List<Long> campaignIds = new ArrayList<>();
      JsonNode campaignsNode = root.get("campaigns");
      if (campaignsNode != null && campaignsNode.isArray()) {
        for (JsonNode campaign : campaignsNode) {
          JsonNode idNode = campaign.get("campaignId");
          if (idNode != null && !idNode.isNull()) {
            campaignIds.add(idNode.asLong());
          }
        }
      }

      return new YandexMetadata(businessId, List.copyOf(campaignIds));
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse Yandex connection metadata: " + e.getMessage(), e);
    }
  }
}
