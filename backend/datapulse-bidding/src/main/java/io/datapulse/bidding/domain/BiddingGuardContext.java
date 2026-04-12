package io.datapulse.bidding.domain;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Context passed to each bidding guard for evaluation.
 *
 * @param marketplaceOfferId  the offer being evaluated
 * @param workspaceId         workspace scope
 * @param signals             assembled signal set for the offer
 * @param proposedDecision    the decision type proposed by the strategy
 * @param targetBid           the bid value proposed by the strategy (kopecks)
 * @param currentBid          the current bid value (kopecks)
 * @param policyConfig        raw JSON config from the bid policy
 */
public record BiddingGuardContext(
    long marketplaceOfferId,
    long workspaceId,
    BiddingSignalSet signals,
    BidDecisionType proposedDecision,
    Integer targetBid,
    Integer currentBid,
    JsonNode policyConfig
) {
}
