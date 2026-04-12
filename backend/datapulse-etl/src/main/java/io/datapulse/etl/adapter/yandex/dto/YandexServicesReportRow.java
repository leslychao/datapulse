package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Stub DTO for United Marketplace Services report rows.
 * Full column set TBD after empirical verification with active account.
 * Yandex may change report structure without notice (F-6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexServicesReportRow(
    Long orderId,
    String yourSku,
    String shopSku,
    String offerName,
    String orderCreationDateTime,
    String placementModel,
    Long partnerId
) {}
