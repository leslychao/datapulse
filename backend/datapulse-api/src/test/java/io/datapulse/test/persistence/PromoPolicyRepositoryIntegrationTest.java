package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestPromoPolicyBuilder.aPromoPolicy;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoDecisionType;
import io.datapulse.promotions.domain.PromoPolicyStatus;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoDecisionRepository;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class PromoPolicyRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PromoPolicyRepository policyRepository;

  @Autowired
  private PromoDecisionRepository decisionRepository;

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
  @DisplayName("PromoPolicyRepository")
  class PolicyTests {

    @Test
    void should_save_and_findByWorkspaceId() {
      policyRepository.save(aPromoPolicy().withWorkspaceId(workspaceId).withName("PP1").build());
      policyRepository.save(aPromoPolicy().withWorkspaceId(workspaceId).withName("PP2").build());

      assertThat(policyRepository.findAllByWorkspaceId(workspaceId)).hasSize(2);
    }

    @Test
    void should_filterByStatus() {
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId)
              .withStatus(PromoPolicyStatus.ACTIVE).build());
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId)
              .withStatus(PromoPolicyStatus.DRAFT).build());

      assertThat(policyRepository.findAllByWorkspaceIdAndStatus(
          workspaceId, PromoPolicyStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void should_findByIdAndWorkspaceId() {
      var saved = policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).build());

      assertThat(policyRepository.findByIdAndWorkspaceId(saved.getId(), workspaceId))
          .isPresent();
      assertThat(policyRepository.findByIdAndWorkspaceId(saved.getId(), 99999L))
          .isEmpty();
    }

    @Test
    void should_existsByWorkspaceIdAndStatus() {
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId)
              .withStatus(PromoPolicyStatus.ACTIVE).build());

      assertThat(policyRepository.existsByWorkspaceIdAndStatus(
          workspaceId, PromoPolicyStatus.ACTIVE)).isTrue();
      assertThat(policyRepository.existsByWorkspaceIdAndStatus(
          workspaceId, PromoPolicyStatus.PAUSED)).isFalse();
    }
  }

  @Nested
  @DisplayName("PromoDecisionRepository")
  class DecisionTests {

    @Test
    void should_save_and_findByWorkspaceId() {
      var decision = createDecision(PromoDecisionType.PARTICIPATE);

      var page = decisionRepository.findAllByWorkspaceId(
          workspaceId, PageRequest.of(0, 10));
      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(decision.getId());
    }

    @Test
    void should_filterByDecisionType() {
      createDecision(PromoDecisionType.PARTICIPATE);
      createDecision(PromoDecisionType.DECLINE);

      var page = decisionRepository.findAllByWorkspaceIdAndDecisionType(
          workspaceId, PromoDecisionType.PARTICIPATE, PageRequest.of(0, 10));
      assertThat(page.getContent()).hasSize(1);
    }

    private PromoDecisionEntity createDecision(PromoDecisionType type) {
      var d = new PromoDecisionEntity();
      d.setWorkspaceId(workspaceId);
      d.setCanonicalPromoProductId(1L);
      d.setPolicyVersion(1);
      d.setPolicySnapshot("{}");
      d.setDecisionType(type);
      d.setParticipationMode(ParticipationMode.RECOMMENDATION);
      d.setExecutionMode("RECOMMENDATION");
      return decisionRepository.save(d);
    }
  }
}
