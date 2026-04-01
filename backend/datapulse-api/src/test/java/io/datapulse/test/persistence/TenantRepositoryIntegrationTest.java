package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.tenancy.domain.TenantStatus;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TenantRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    void should_persistTenant_and_assignId() {
      var user = appUserRepository.save(aUser().build());
      var tenant = aTenant().withOwnerUserId(user.getId()).build();

      var saved = tenantRepository.save(tenant);

      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getUpdatedAt()).isNotNull();
      assertThat(saved.getName()).isEqualTo("Test Tenant");
      assertThat(saved.getSlug()).isEqualTo("test-tenant");
      assertThat(saved.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void should_returnTenant_when_exists() {
      var user = appUserRepository.save(aUser().build());
      var saved = tenantRepository.save(
          aTenant().withOwnerUserId(user.getId()).build());

      var found = tenantRepository.findById(saved.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getSlug()).isEqualTo("test-tenant");
    }

    @Test
    void should_returnEmpty_when_notExists() {
      assertThat(tenantRepository.findById(99999L)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findBySlug")
  class FindBySlug {

    @Test
    void should_returnTenant_when_slugMatches() {
      var user = appUserRepository.save(aUser().build());
      tenantRepository.save(
          aTenant().withSlug("unique-slug").withOwnerUserId(user.getId()).build());

      var found = tenantRepository.findBySlug("unique-slug");

      assertThat(found).isPresent();
      assertThat(found.get().getName()).isEqualTo("Test Tenant");
    }

    @Test
    void should_returnEmpty_when_slugNotFound() {
      assertThat(tenantRepository.findBySlug("non-existent")).isEmpty();
    }
  }

  @Nested
  @DisplayName("existsBySlug")
  class ExistsBySlug {

    @Test
    void should_returnTrue_when_slugExists() {
      var user = appUserRepository.save(aUser().build());
      tenantRepository.save(
          aTenant().withSlug("existing-slug").withOwnerUserId(user.getId()).build());

      assertThat(tenantRepository.existsBySlug("existing-slug")).isTrue();
    }

    @Test
    void should_returnFalse_when_slugNotExists() {
      assertThat(tenantRepository.existsBySlug("phantom-slug")).isFalse();
    }
  }

  @Nested
  @DisplayName("countByOwnerUserId")
  class CountByOwnerUserId {

    @Test
    void should_countTenants_ownedByUser() {
      var user = appUserRepository.save(aUser().build());
      tenantRepository.save(
          aTenant().withSlug("t1").withOwnerUserId(user.getId()).build());
      tenantRepository.save(
          aTenant().withSlug("t2").withOwnerUserId(user.getId()).build());

      assertThat(tenantRepository.countByOwnerUserId(user.getId())).isEqualTo(2);
    }
  }
}
