package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinancePosting(
    @JsonProperty("delivery_schema") String deliverySchema,
    @JsonProperty("order_date") String orderDate,
    @JsonProperty("posting_number") String postingNumber,
    @JsonProperty("warehouse_id") long warehouseId
) {}
