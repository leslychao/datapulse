package io.datapulse.bidding.persistence;

/**
 * Latest advertising campaign info linked to an offer through
 * fact_advertising or canonical_advertising_campaign.
 */
public record CampaignInfoRow(
    String campaignExternalId,
    String status,
    String marketplaceType
) {
}
