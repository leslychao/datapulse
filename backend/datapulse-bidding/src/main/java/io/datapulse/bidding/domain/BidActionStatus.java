package io.datapulse.bidding.domain;

public enum BidActionStatus {

  PENDING_APPROVAL,
  APPROVED,
  SCHEDULED,
  EXECUTING,
  RECONCILIATION_PENDING,
  SUCCEEDED,
  FAILED,
  RETRY_SCHEDULED,
  EXPIRED,
  SUPERSEDED,
  CANCELLED,
  ON_HOLD;

  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED
        || this == EXPIRED || this == SUPERSEDED || this == CANCELLED;
  }

  public boolean isPreExecution() {
    return this == PENDING_APPROVAL || this == APPROVED
        || this == SCHEDULED || this == ON_HOLD;
  }
}
