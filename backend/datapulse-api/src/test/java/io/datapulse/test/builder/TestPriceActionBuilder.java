package io.datapulse.test.builder;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.persistence.PriceActionEntity;

import java.math.BigDecimal;

public class TestPriceActionBuilder {

  private Long workspaceId;
  private Long marketplaceOfferId;
  private Long priceDecisionId;
  private ActionExecutionMode executionMode = ActionExecutionMode.LIVE;
  private ActionStatus status = ActionStatus.PENDING_APPROVAL;
  private BigDecimal targetPrice = BigDecimal.valueOf(999);
  private BigDecimal currentPriceAtCreation = BigDecimal.valueOf(1099);
  private int attemptCount = 0;
  private int maxAttempts = 3;
  private int approvalTimeoutHours = 24;

  public static TestPriceActionBuilder aPriceAction() {
    return new TestPriceActionBuilder();
  }

  public TestPriceActionBuilder withWorkspaceId(Long workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public TestPriceActionBuilder withMarketplaceOfferId(Long marketplaceOfferId) {
    this.marketplaceOfferId = marketplaceOfferId;
    return this;
  }

  public TestPriceActionBuilder withPriceDecisionId(Long priceDecisionId) {
    this.priceDecisionId = priceDecisionId;
    return this;
  }

  public TestPriceActionBuilder withExecutionMode(ActionExecutionMode executionMode) {
    this.executionMode = executionMode;
    return this;
  }

  public TestPriceActionBuilder withStatus(ActionStatus status) {
    this.status = status;
    return this;
  }

  public TestPriceActionBuilder withTargetPrice(BigDecimal targetPrice) {
    this.targetPrice = targetPrice;
    return this;
  }

  public PriceActionEntity build() {
    var entity = new PriceActionEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setMarketplaceOfferId(marketplaceOfferId);
    entity.setPriceDecisionId(priceDecisionId);
    entity.setExecutionMode(executionMode);
    entity.setStatus(status);
    entity.setTargetPrice(targetPrice);
    entity.setCurrentPriceAtCreation(currentPriceAtCreation);
    entity.setAttemptCount(attemptCount);
    entity.setMaxAttempts(maxAttempts);
    entity.setApprovalTimeoutHours(approvalTimeoutHours);
    return entity;
  }
}
