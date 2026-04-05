package io.datapulse.etl.adapter.ozon.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SKU-level advertising statistics row from Ozon Performance API
 * ({@code GET /api/client/statistics/campaign/product}).
 * Grain: campaign × date × SKU.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPerformanceStatDto(
    @JsonProperty("campaign_id") long campaignId,
    String date,
    long sku,
    long views,
    long clicks,
    BigDecimal spend,
    int orders,
    BigDecimal revenue
) {}
