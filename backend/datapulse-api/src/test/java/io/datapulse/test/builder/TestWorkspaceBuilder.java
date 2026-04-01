package io.datapulse.test.builder;

import io.datapulse.tenancy.domain.WorkspaceStatus;
import io.datapulse.tenancy.persistence.TenantEntity;
import io.datapulse.tenancy.persistence.WorkspaceEntity;

import java.util.UUID;

public class TestWorkspaceBuilder {

  private TenantEntity tenant;
  private String name = "Test Workspace";
  private String slug = "test-ws-" + UUID.randomUUID().toString().substring(0, 8);
  private WorkspaceStatus status = WorkspaceStatus.ACTIVE;
  private Long ownerUserId = 1L;

  public static TestWorkspaceBuilder aWorkspace() {
    return new TestWorkspaceBuilder();
  }

  public TestWorkspaceBuilder withTenant(TenantEntity tenant) {
    this.tenant = tenant;
    return this;
  }

  public TestWorkspaceBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestWorkspaceBuilder withSlug(String slug) {
    this.slug = slug;
    return this;
  }

  public TestWorkspaceBuilder withStatus(WorkspaceStatus status) {
    this.status = status;
    return this;
  }

  public TestWorkspaceBuilder withOwnerUserId(Long ownerUserId) {
    this.ownerUserId = ownerUserId;
    return this;
  }

  public WorkspaceEntity build() {
    var entity = new WorkspaceEntity();
    entity.setTenant(tenant);
    entity.setName(name);
    entity.setSlug(slug);
    entity.setStatus(status);
    entity.setOwnerUserId(ownerUserId);
    return entity;
  }
}
