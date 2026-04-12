package io.datapulse.etl.adapter.yandex.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for United Marketplace Services report rows.
 * Report structure may change without notice (F-6).
 *
 * <p>The report contains multiple sheets (placement, sale_commission,
 * money_withdraw, paid_storage, etc.), all flattened into a single JSON array.</p>
 *
 * <p>Key fields for P&L normalization:
 * <ul>
 *   <li>{@code serviceName} — discriminator for entry type mapping</li>
 *   <li>{@code totalAmount} — cost of service (expected: positive = charge to seller)</li>
 * </ul></p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexServicesReportRow(
    Long orderId,
    String shopSku,
    String offerName,
    String orderCreationDateTime,
    String placementModel,
    Long partnerId,
    String inn,
    BigDecimal price,
    Integer count,
    String serviceName,
    String serviceDateTime,
    String actDate,
    BigDecimal tariff,
    BigDecimal amountWithoutBonuses,
    BigDecimal totalAmount,
    BigDecimal feeBenefitDiscount,
    BigDecimal qualityIndexAmount,
    BigDecimal individualDiscount,
    BigDecimal loyaltyDiscount,
    BigDecimal netting
) {}
