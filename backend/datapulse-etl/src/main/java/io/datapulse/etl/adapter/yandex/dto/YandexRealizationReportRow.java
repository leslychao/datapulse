package io.datapulse.etl.adapter.yandex.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Stub DTO for Goods Realization report rows.
 * Full column set TBD after empirical verification with active account.
 * Yandex may change report structure without notice (F-6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexRealizationReportRow(
    Long orderId,
    String yourOrderId,
    String yourSku,
    String shopSku,
    String orderCreationDate,
    String deliveryDate,
    Integer transferredToDeliveryCount,
    BigDecimal priceWithVatAndNoDiscount,
    String vat
) {}
