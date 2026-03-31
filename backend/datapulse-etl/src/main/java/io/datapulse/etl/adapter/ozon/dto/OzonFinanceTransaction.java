package io.datapulse.etl.adapter.ozon.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceTransaction(
        @JsonProperty("operation_id") long operationId,
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("operation_date") String operationDate,
        @JsonProperty("operation_type_name") String operationTypeName,
        @JsonProperty("accruals_for_sale") BigDecimal accrualsForSale,
        @JsonProperty("sale_commission") BigDecimal saleCommission,
        BigDecimal amount,
        String type,
        OzonFinancePosting posting,
        List<OzonFinanceService> services,
        List<OzonFinanceItem> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonFinancePosting(
            @JsonProperty("delivery_schema") String deliverySchema,
            @JsonProperty("order_date") String orderDate,
            @JsonProperty("posting_number") String postingNumber,
            @JsonProperty("warehouse_id") long warehouseId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonFinanceService(
            String name,
            BigDecimal price
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonFinanceItem(
            String name,
            @JsonProperty("sku") long sku
    ) {}
}
