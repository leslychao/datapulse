package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestSavedViewBuilder.aSavedView;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.sellerops.persistence.SavedViewRepository;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionEntity;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SavedViewRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private SavedViewRepository savedViewRepository;

  @Autowired
  private WorkingQueueDefinitionRepository queueRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  private Long workspaceId;
  private Long userId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    userId = user.getId();
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(user.getId()).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(user.getId()).build());
    workspaceId = ws.getId();
  }

  @Nested
  @DisplayName("SavedViewRepository")
  class ViewTests {

    @Test
    void should_save_and_findByWorkspaceAndUser() {
      savedViewRepository.save(
          aSavedView().withWorkspaceId(workspaceId).withUserId(userId)
              .withName("View 1").build());
      savedViewRepository.save(
          aSavedView().withWorkspaceId(workspaceId).withUserId(userId)
              .withName("View 2").build());

      var views = savedViewRepository.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(
          workspaceId, userId);
      assertThat(views).hasSize(2);
    }

    @Test
    void should_findByIdAndWorkspaceId() {
      var saved = savedViewRepository.save(
          aSavedView().withWorkspaceId(workspaceId).withUserId(userId).build());

      assertThat(savedViewRepository.findByIdAndWorkspaceId(saved.getId(), workspaceId))
          .isPresent();
      assertThat(savedViewRepository.findByIdAndWorkspaceId(saved.getId(), 99999L))
          .isEmpty();
    }

    @Test
    void should_checkNameUniqueness() {
      savedViewRepository.save(
          aSavedView().withWorkspaceId(workspaceId).withUserId(userId)
              .withName("Unique View").build());

      assertThat(savedViewRepository.existsByWorkspaceIdAndUserIdAndName(
          workspaceId, userId, "Unique View")).isTrue();
      assertThat(savedViewRepository.existsByWorkspaceIdAndUserIdAndName(
          workspaceId, userId, "Other View")).isFalse();
    }
  }

  @Nested
  @DisplayName("WorkingQueueDefinitionRepository")
  class QueueTests {

    @Test
    void should_save_and_findByWorkspaceId() {
      queueRepository.save(createQueue("Queue 1", true));
      queueRepository.save(createQueue("Queue 2", false));

      var queues = queueRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
      assertThat(queues).hasSize(2);
    }

    @Test
    void should_checkNameUniqueness() {
      queueRepository.save(createQueue("Unique Queue", true));

      assertThat(queueRepository.existsByWorkspaceIdAndName(workspaceId, "Unique Queue"))
          .isTrue();
      assertThat(queueRepository.existsByWorkspaceIdAndName(workspaceId, "Other"))
          .isFalse();
    }

    @Test
    void should_findEnabledWithAutoCriteria() {
      var q1 = createQueue("Auto Queue", true);
      q1.setAutoCriteria(Map.of("status", "active"));
      queueRepository.save(q1);

      var q2 = createQueue("Manual Queue", true);
      q2.setAutoCriteria(null);
      queueRepository.save(q2);

      var q3 = createQueue("Disabled Auto", false);
      q3.setAutoCriteria(Map.of("status", "active"));
      queueRepository.save(q3);

      var found = queueRepository.findAllEnabledWithAutoCriteria();
      assertThat(found).hasSize(1);
      assertThat(found.get(0).getName()).isEqualTo("Auto Queue");
    }

    private WorkingQueueDefinitionEntity createQueue(String name, boolean enabled) {
      var entity = new WorkingQueueDefinitionEntity();
      entity.setWorkspaceId(workspaceId);
      entity.setName(name);
      entity.setQueueType("MANUAL");
      entity.setEnabled(enabled);
      entity.setSystem(false);
      return entity;
    }
  }
}
