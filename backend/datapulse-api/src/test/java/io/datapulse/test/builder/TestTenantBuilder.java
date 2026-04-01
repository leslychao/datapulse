package io.datapulse.test.builder;

import io.datapulse.tenancy.domain.TenantStatus;
import io.datapulse.tenancy.persistence.TenantEntity;

public class TestTenantBuilder {

  private String name = "Test Tenant";
  private String slug = "test-tenant";
  private TenantStatus status = TenantStatus.ACTIVE;
  private Long ownerUserId = 1L;

  public static TestTenantBuilder aTenant() {
    return new TestTenantBuilder();
  }

  public TestTenantBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestTenantBuilder withSlug(String slug) {
    this.slug = slug;
    return this;
  }

  public TestTenantBuilder withStatus(TenantStatus status) {
    this.status = status;
    return this;
  }

  public TestTenantBuilder withOwnerUserId(Long ownerUserId) {
    this.ownerUserId = ownerUserId;
    return this;
  }

  public TenantEntity build() {
    var entity = new TenantEntity();
    entity.setName(name);
    entity.setSlug(slug);
    entity.setStatus(status);
    entity.setOwnerUserId(ownerUserId);
    return entity;
  }
}
