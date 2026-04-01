package io.datapulse.test.builder;

import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoPolicyStatus;
import io.datapulse.promotions.persistence.PromoPolicyEntity;

import java.math.BigDecimal;

public class TestPromoPolicyBuilder {

  private Long workspaceId;
  private String name = "Test Promo Policy";
  private PromoPolicyStatus status = PromoPolicyStatus.DRAFT;
  private ParticipationMode participationMode = ParticipationMode.RECOMMENDATION;
  private BigDecimal minMarginPct = BigDecimal.valueOf(5);
  private Integer minStockDaysOfCover = 7;
  private BigDecimal maxPromoDiscountPct = BigDecimal.valueOf(30);
  private Integer version = 1;
  private Long createdBy = 1L;

  public static TestPromoPolicyBuilder aPromoPolicy() {
    return new TestPromoPolicyBuilder();
  }

  public TestPromoPolicyBuilder withWorkspaceId(Long workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public TestPromoPolicyBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestPromoPolicyBuilder withStatus(PromoPolicyStatus status) {
    this.status = status;
    return this;
  }

  public PromoPolicyEntity build() {
    var entity = new PromoPolicyEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setName(name);
    entity.setStatus(status);
    entity.setParticipationMode(participationMode);
    entity.setMinMarginPct(minMarginPct);
    entity.setMinStockDaysOfCover(minStockDaysOfCover);
    entity.setMaxPromoDiscountPct(maxPromoDiscountPct);
    entity.setVersion(version);
    entity.setCreatedBy(createdBy);
    return entity;
  }
}
