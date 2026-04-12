package io.datapulse.etl.adapter.yandex.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Goods Realization report rows.
 * Monthly granularity only (year + month).
 * Report structure may change without notice (F-6).
 *
 * <p>Revenue calculation: {@code priceWithVatAndNoDiscount × transferredToDeliveryCount}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexRealizationReportRow(
    Long orderId,
    String yourOrderId,
    String yourSku,
    String shopSku,
    String offerName,
    String orderCreationDate,
    String transferredToDeliveryDate,
    String deliveryDate,
    Integer transferredToDeliveryCount,
    BigDecimal priceWithVatAndNoDiscount,
    String vat,
    String placementModel,
    Long partnerId
) {}
