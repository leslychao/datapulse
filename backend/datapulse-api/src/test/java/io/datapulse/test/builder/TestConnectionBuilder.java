package io.datapulse.test.builder;

import io.datapulse.integration.persistence.MarketplaceConnectionEntity;

public class TestConnectionBuilder {

  private Long workspaceId;
  private String marketplaceType = "WB";
  private String name = "Test Connection";
  private String status = "ACTIVE";
  private String externalAccountId = "ext-123";
  private Long secretReferenceId;

  public static TestConnectionBuilder aConnection() {
    return new TestConnectionBuilder();
  }

  public TestConnectionBuilder withWorkspaceId(Long workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public TestConnectionBuilder withMarketplaceType(String marketplaceType) {
    this.marketplaceType = marketplaceType;
    return this;
  }

  public TestConnectionBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestConnectionBuilder withStatus(String status) {
    this.status = status;
    return this;
  }

  public TestConnectionBuilder withExternalAccountId(String externalAccountId) {
    this.externalAccountId = externalAccountId;
    return this;
  }

  public TestConnectionBuilder withSecretReferenceId(Long secretReferenceId) {
    this.secretReferenceId = secretReferenceId;
    return this;
  }

  public MarketplaceConnectionEntity build() {
    var entity = new MarketplaceConnectionEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setMarketplaceType(marketplaceType);
    entity.setName(name);
    entity.setStatus(status);
    entity.setExternalAccountId(externalAccountId);
    entity.setSecretReferenceId(secretReferenceId);
    return entity;
  }
}
