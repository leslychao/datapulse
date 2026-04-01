package io.datapulse.pricing.domain;

import io.datapulse.pricing.persistence.PricePolicyEntity;

/**
 * An offer that passed eligibility check and has a resolved effective policy.
 */
public record EligibleOffer(
        long marketplaceOfferId,
        long sellerSkuId,
        Long categoryId,
        long connectionId,
        PricePolicyEntity policy
) {
}
