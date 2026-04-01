package io.datapulse.test.builder;

import io.datapulse.tenancy.domain.UserStatus;
import io.datapulse.tenancy.persistence.AppUserEntity;

import java.util.UUID;

public class TestUserBuilder {

  private String externalId = UUID.randomUUID().toString();
  private String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
  private String name = "Test User";
  private UserStatus status = UserStatus.ACTIVE;

  public static TestUserBuilder aUser() {
    return new TestUserBuilder();
  }

  public TestUserBuilder withExternalId(String externalId) {
    this.externalId = externalId;
    return this;
  }

  public TestUserBuilder withEmail(String email) {
    this.email = email;
    return this;
  }

  public TestUserBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestUserBuilder withStatus(UserStatus status) {
    this.status = status;
    return this;
  }

  public AppUserEntity build() {
    var entity = new AppUserEntity();
    entity.setExternalId(externalId);
    entity.setEmail(email);
    entity.setName(name);
    entity.setStatus(status);
    return entity;
  }
}
