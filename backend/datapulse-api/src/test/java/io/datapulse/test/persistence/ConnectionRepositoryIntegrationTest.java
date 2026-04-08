package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestConnectionBuilder.aConnection;
import static io.datapulse.test.builder.TestSecretReferenceBuilder.aSecretReference;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConnectionRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MarketplaceConnectionRepository connectionRepository;

  @Autowired
  private SecretReferenceRepository secretRefRepository;

  @Autowired
  private MarketplaceSyncStateRepository syncStateRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  private Long workspaceId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(user.getId()).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(user.getId()).build());
    workspaceId = ws.getId();
  }

  @Nested
  @DisplayName("MarketplaceConnectionRepository")
  class ConnectionTests {

    @Test
    void should_save_and_findByWorkspaceId() {
      var secret = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      var conn = connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withSecretReferenceId(secret.getId()).build());

      var found = connectionRepository.findAllByWorkspaceId(workspaceId);

      assertThat(found).hasSize(1);
      assertThat(found.get(0).getId()).isEqualTo(conn.getId());
      assertThat(found.get(0).getMarketplaceType()).isEqualTo("WB");
    }

    @Test
    void should_findByIdAndWorkspaceId() {
      var secret = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      var conn = connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withSecretReferenceId(secret.getId()).build());

      assertThat(connectionRepository.findByIdAndWorkspaceId(conn.getId(), workspaceId))
          .isPresent();
      assertThat(connectionRepository.findByIdAndWorkspaceId(conn.getId(), 99999L))
          .isEmpty();
    }

    @Test
    void should_findByStatus() {
      var secret = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId).withStatus("ACTIVE")
              .withSecretReferenceId(secret.getId()).build());
      connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId).withStatus("DISABLED")
              .withExternalAccountId("ext-456")
              .withSecretReferenceId(secret.getId()).build());

      assertThat(connectionRepository.findAllByStatus("ACTIVE")).hasSize(1);
      assertThat(connectionRepository.findAllByStatus("DISABLED")).hasSize(1);
    }

    @Test
    void should_detectDuplicate_excludingSelfAndArchived() {
      var secret = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      var conn = connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withMarketplaceType("WB").withExternalAccountId("acc-1")
              .withStatus("ACTIVE")
              .withSecretReferenceId(secret.getId()).build());

      assertThat(connectionRepository
          .existsByWorkspaceIdAndMarketplaceTypeAndExternalAccountIdAndIdNotAndStatusNot(
              workspaceId, "WB", "acc-1", conn.getId(), "ARCHIVED")).isFalse();

      var secret2 = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withMarketplaceType("WB").withExternalAccountId("acc-1")
              .withStatus("ARCHIVED")
              .withSecretReferenceId(secret2.getId()).build());

      assertThat(connectionRepository
          .existsByWorkspaceIdAndMarketplaceTypeAndExternalAccountIdAndIdNotAndStatusNot(
              workspaceId, "WB", "acc-1", conn.getId(), "ARCHIVED")).isFalse();

      var secret3 = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withMarketplaceType("OZON").withExternalAccountId("acc-1")
              .withStatus("ACTIVE")
              .withSecretReferenceId(secret3.getId()).build());

      assertThat(connectionRepository
          .existsByWorkspaceIdAndMarketplaceTypeAndExternalAccountIdAndIdNotAndStatusNot(
              workspaceId, "WB", "acc-1", conn.getId(), "ARCHIVED")).isFalse();
    }
  }

  @Nested
  @DisplayName("SecretReferenceRepository")
  class SecretRefTests {

    @Test
    void should_save_and_findById() {
      var saved = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());

      assertThat(saved.getId()).isNotNull();
      assertThat(secretRefRepository.findById(saved.getId())).isPresent();
    }
  }

  @Nested
  @DisplayName("MarketplaceSyncStateRepository")
  class SyncStateTests {

    @Test
    void should_findByConnectionId() {
      var secret = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      var conn = connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withSecretReferenceId(secret.getId()).build());

      var syncState = new MarketplaceSyncStateEntity();
      syncState.setMarketplaceConnectionId(conn.getId());
      syncState.setDataDomain("PRODUCTS");
      syncState.setStatus("IDLE");
      syncStateRepository.save(syncState);

      var found = syncStateRepository.findAllByMarketplaceConnectionId(conn.getId());
      assertThat(found).hasSize(1);
      assertThat(found.get(0).getDataDomain()).isEqualTo("PRODUCTS");
    }

    @Test
    void should_findEligibleForSync() {
      var secret = secretRefRepository.save(
          aSecretReference().withWorkspaceId(workspaceId).build());
      var conn = connectionRepository.save(
          aConnection().withWorkspaceId(workspaceId)
              .withSecretReferenceId(secret.getId()).build());

      var eligible = new MarketplaceSyncStateEntity();
      eligible.setMarketplaceConnectionId(conn.getId());
      eligible.setDataDomain("ORDERS");
      eligible.setStatus("IDLE");
      eligible.setNextScheduledAt(OffsetDateTime.now().minusMinutes(5));
      syncStateRepository.save(eligible);

      var notYet = new MarketplaceSyncStateEntity();
      notYet.setMarketplaceConnectionId(conn.getId());
      notYet.setDataDomain("PRICES");
      notYet.setStatus("IDLE");
      notYet.setNextScheduledAt(OffsetDateTime.now().plusHours(1));
      syncStateRepository.save(notYet);

      var result = syncStateRepository.findEligibleForSync(OffsetDateTime.now());
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getDataDomain()).isEqualTo("ORDERS");
    }
  }
}
