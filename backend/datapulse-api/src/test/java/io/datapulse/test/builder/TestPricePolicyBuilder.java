package io.datapulse.test.builder;

import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.persistence.PricePolicyEntity;

import java.math.BigDecimal;

public class TestPricePolicyBuilder {

  private Long workspaceId;
  private String name = "Test Policy";
  private PolicyStatus status = PolicyStatus.DRAFT;
  private PolicyType strategyType = PolicyType.TARGET_MARGIN;
  private String strategyParams = "{}";
  private BigDecimal minMarginPct = BigDecimal.valueOf(10);
  private BigDecimal maxPriceChangePct = BigDecimal.valueOf(15);
  private String guardConfig = "{}";
  private ExecutionMode executionMode = ExecutionMode.RECOMMENDATION;
  private Integer approvalTimeoutHours = 24;
  private Integer priority = 1;
  private Integer version = 1;
  private Long createdBy = 1L;

  public static TestPricePolicyBuilder aPricePolicy() {
    return new TestPricePolicyBuilder();
  }

  public TestPricePolicyBuilder withWorkspaceId(Long workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public TestPricePolicyBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestPricePolicyBuilder withStatus(PolicyStatus status) {
    this.status = status;
    return this;
  }

  public TestPricePolicyBuilder withStrategyType(PolicyType strategyType) {
    this.strategyType = strategyType;
    return this;
  }

  public TestPricePolicyBuilder withExecutionMode(ExecutionMode executionMode) {
    this.executionMode = executionMode;
    return this;
  }

  public PricePolicyEntity build() {
    var entity = new PricePolicyEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setName(name);
    entity.setStatus(status);
    entity.setStrategyType(strategyType);
    entity.setStrategyParams(strategyParams);
    entity.setMinMarginPct(minMarginPct);
    entity.setMaxPriceChangePct(maxPriceChangePct);
    entity.setGuardConfig(guardConfig);
    entity.setExecutionMode(executionMode);
    entity.setApprovalTimeoutHours(approvalTimeoutHours);
    entity.setPriority(priority);
    entity.setVersion(version);
    entity.setCreatedBy(createdBy);
    return entity;
  }
}
