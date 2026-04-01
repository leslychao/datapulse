package io.datapulse.test.builder;

import io.datapulse.integration.persistence.SecretReferenceEntity;

public class TestSecretReferenceBuilder {

  private Long workspaceId;
  private String provider = "VAULT";
  private String vaultPath = "secret/data/connections/test";
  private String vaultKey = "api-token";
  private Integer vaultVersion = 1;
  private String secretType = "API_TOKEN";
  private String status = "ACTIVE";

  public static TestSecretReferenceBuilder aSecretReference() {
    return new TestSecretReferenceBuilder();
  }

  public TestSecretReferenceBuilder withWorkspaceId(Long workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public TestSecretReferenceBuilder withProvider(String provider) {
    this.provider = provider;
    return this;
  }

  public TestSecretReferenceBuilder withSecretType(String secretType) {
    this.secretType = secretType;
    return this;
  }

  public TestSecretReferenceBuilder withStatus(String status) {
    this.status = status;
    return this;
  }

  public SecretReferenceEntity build() {
    var entity = new SecretReferenceEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setProvider(provider);
    entity.setVaultPath(vaultPath);
    entity.setVaultKey(vaultKey);
    entity.setVaultVersion(vaultVersion);
    entity.setSecretType(secretType);
    entity.setStatus(status);
    return entity;
  }
}
