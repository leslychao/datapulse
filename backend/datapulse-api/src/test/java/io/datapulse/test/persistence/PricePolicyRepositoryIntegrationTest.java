package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestPricePolicyBuilder.aPricePolicy;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.test.builder.TestConnectionBuilder;
import io.datapulse.test.builder.TestSecretReferenceBuilder;

import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import io.datapulse.pricing.domain.DecisionType;
import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class PricePolicyRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PricePolicyRepository policyRepository;

  @Autowired
  private PriceDecisionRepository decisionRepository;

  @Autowired
  private PricingRunRepository runRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  @Autowired
  private SecretReferenceRepository secretRefRepository;

  @Autowired
  private MarketplaceConnectionRepository connectionRepository;

  private Long workspaceId;
  private Long connectionId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(user.getId()).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(user.getId()).build());
    workspaceId = ws.getId();

    var secret = secretRefRepository.save(
        TestSecretReferenceBuilder.aSecretReference().withWorkspaceId(workspaceId).build());
    var conn = connectionRepository.save(
        TestConnectionBuilder.aConnection().withWorkspaceId(workspaceId)
            .withSecretReferenceId(secret.getId()).build());
    connectionId = conn.getId();
  }

  @Nested
  @DisplayName("PricePolicyRepository")
  class PolicyTests {

    @Test
    void should_save_and_findByWorkspaceId() {
      policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId).withName("P1").build());
      policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId).withName("P2").build());

      var found = policyRepository.findAllByWorkspaceId(workspaceId);
      assertThat(found).hasSize(2);
    }

    @Test
    void should_filterByStatus() {
      policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId)
              .withStatus(PolicyStatus.ACTIVE).build());
      policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId)
              .withStatus(PolicyStatus.DRAFT).build());

      assertThat(policyRepository.findAllByWorkspaceIdAndStatus(
          workspaceId, PolicyStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void should_filterByStrategyType() {
      policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId)
              .withStrategyType(PolicyType.TARGET_MARGIN).build());
      policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId)
              .withStrategyType(PolicyType.PRICE_CORRIDOR).build());

      assertThat(policyRepository.findAllByWorkspaceIdAndStrategyType(
          workspaceId, PolicyType.PRICE_CORRIDOR)).hasSize(1);
    }

    @Test
    void should_findByIdAndWorkspaceId() {
      var saved = policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId).build());

      assertThat(policyRepository.findByIdAndWorkspaceId(saved.getId(), workspaceId))
          .isPresent();
      assertThat(policyRepository.findByIdAndWorkspaceId(saved.getId(), 99999L))
          .isEmpty();
    }

    @Test
    void should_supportActivateAndArchiveTransitions() {
      var policy = policyRepository.save(
          aPricePolicy().withWorkspaceId(workspaceId)
              .withStatus(PolicyStatus.DRAFT).build());

      policy.setStatus(PolicyStatus.ACTIVE);
      policyRepository.save(policy);
      assertThat(policyRepository.findById(policy.getId()).get().getStatus())
          .isEqualTo(PolicyStatus.ACTIVE);

      policy.setStatus(PolicyStatus.ARCHIVED);
      policyRepository.save(policy);
      assertThat(policyRepository.findById(policy.getId()).get().getStatus())
          .isEqualTo(PolicyStatus.ARCHIVED);
    }
  }

  @Nested
  @DisplayName("PriceDecisionRepository")
  class DecisionTests {

    @Test
    void should_save_and_findByRunId() {
      var run = createRun();
      var decision = createDecision(run.getId());

      var page = decisionRepository.findAllByPricingRunId(
          run.getId(), PageRequest.of(0, 10));
      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(decision.getId());
    }

    @Test
    void should_findLatestByOffer() {
      var run = createRun();
      var d1 = createDecision(run.getId());

      var latest = decisionRepository.findLatestByOffer(1L, PageRequest.of(0, 1));
      assertThat(latest).hasSize(1);
      assertThat(latest.get(0).getId()).isEqualTo(d1.getId());
    }

    private PricingRunEntity createRun() {
      var run = new PricingRunEntity();
      run.setWorkspaceId(workspaceId);
      run.setConnectionId(connectionId);
      run.setStatus(RunStatus.COMPLETED);
      run.setTriggerType(RunTriggerType.MANUAL);
      run.setTotalOffers(1);
      return runRepository.save(run);
    }

    private PriceDecisionEntity createDecision(Long runId) {
      var d = new PriceDecisionEntity();
      d.setWorkspaceId(workspaceId);
      d.setPricingRunId(runId);
      d.setMarketplaceOfferId(1L);
      d.setPolicyVersion(1);
      d.setDecisionType(DecisionType.CHANGE);
      d.setStrategyType(PolicyType.TARGET_MARGIN);
      d.setCurrentPrice(BigDecimal.valueOf(1000));
      d.setTargetPrice(BigDecimal.valueOf(900));
      d.setPriceChangeAmount(BigDecimal.valueOf(-100));
      d.setPriceChangePct(BigDecimal.valueOf(-10));
      d.setExecutionMode("RECOMMENDATION");
      return decisionRepository.save(d);
    }
  }
}
