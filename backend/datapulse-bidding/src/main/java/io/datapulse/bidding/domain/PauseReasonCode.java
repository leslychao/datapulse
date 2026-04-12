package io.datapulse.bidding.domain;

/**
 * Structured reason why a PAUSE decision was made.
 * Used by {@link BiddingResumeEvaluator} to reliably detect
 * when the pause condition has resolved, without parsing text.
 */
public enum PauseReasonCode {

  STOCK_OUT,
  NEGATIVE_MARGIN,
  DRR_CRITICAL,
  GUARD_BLOCK
}
