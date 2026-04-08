package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestConnectionBuilder.aConnection;
import static io.datapulse.test.builder.TestPriceActionBuilder.aPriceAction;
import static io.datapulse.test.builder.TestSecretReferenceBuilder.aSecretReference;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferEntity;
import io.datapulse.etl.persistence.canonical.ProductMasterEntity;
import io.datapulse.etl.persistence.canonical.SellerSkuEntity;
import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionReconciliationSource;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import io.datapulse.pricing.domain.DecisionType;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PriceActionRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PriceActionRepository actionRepository;

  @Autowired
  private PriceActionCasRepository casRepository;

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

  @Autowired
  private JobExecutionRepository jobExecutionRepository;

  @Autowired
  private PricingRunRepository pricingRunRepository;

  @Autowired
  private PriceDecisionRepository priceDecisionRepository;

  @Autowired
  private EntityManager em;

  private Long workspaceId;
  private Long userId;
  private Long marketplaceOfferId;
  private Long priceDecisionId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    userId = user.getId();
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(userId).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(user.getId()).build());
    workspaceId = ws.getId();

    var secret = secretRefRepository.save(
        aSecretReference().withWorkspaceId(workspaceId).build());
    var conn = connectionRepository.save(
        aConnection().withWorkspaceId(workspaceId)
            .withSecretReferenceId(secret.getId()).build());

    long jobId = jobExecutionRepository.insert(conn.getId(), "FULL_SYNC");

    var pm = new ProductMasterEntity();
    pm.setWorkspaceId(workspaceId);
    pm.setExternalCode("test-code");
    pm.setName("Test Product");
    pm.setJobExecutionId(jobId);
    em.persist(pm);

    var sku = new SellerSkuEntity();
    sku.setProductMasterId(pm.getId());
    sku.setSkuCode("test-sku");
    sku.setJobExecutionId(jobId);
    em.persist(sku);

    var offer = new MarketplaceOfferEntity();
    offer.setSellerSkuId(sku.getId());
    offer.setMarketplaceConnectionId(conn.getId());
    offer.setMarketplaceType("WB");
    offer.setMarketplaceSku("test-msku");
    offer.setName("Test Offer");
    offer.setStatus("ACTIVE");
    offer.setJobExecutionId(jobId);
    em.persist(offer);
    em.flush();

    marketplaceOfferId = offer.getId();

    var run = new PricingRunEntity();
    run.setWorkspaceId(workspaceId);
    run.setConnectionId(conn.getId());
    run.setTriggerType(RunTriggerType.MANUAL);
    run.setStatus(RunStatus.COMPLETED);
    pricingRunRepository.save(run);

    var decision = new PriceDecisionEntity();
    decision.setWorkspaceId(workspaceId);
    decision.setPricingRunId(run.getId());
    decision.setMarketplaceOfferId(marketplaceOfferId);
    decision.setPolicyVersion(1);
    decision.setDecisionType(DecisionType.CHANGE);
    decision.setStrategyType(PolicyType.TARGET_MARGIN);
    decision.setCurrentPrice(BigDecimal.valueOf(1099));
    decision.setTargetPrice(BigDecimal.valueOf(999));
    decision.setExecutionMode("LIVE");
    priceDecisionRepository.save(decision);

    priceDecisionId = decision.getId();
  }

  @Nested
  @DisplayName("save and findActiveByOfferAndMode")
  class FindActive {

    @Test
    void should_findActive_when_notTerminal() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      var found = actionRepository.findActiveByOfferAndMode(
          action.getMarketplaceOfferId(), ActionExecutionMode.LIVE);

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(action.getId());
    }

    @Test
    void should_returnEmpty_when_allTerminal() {
      var action = saveAction(ActionStatus.SUCCEEDED);

      var found = actionRepository.findActiveByOfferAndMode(
          action.getMarketplaceOfferId(), ActionExecutionMode.LIVE);

      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("CAS operations")
  class CasOperations {

    @Test
    void should_casTransition_when_statusMatches() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casTransition(
          action.getId(), ActionStatus.PENDING_APPROVAL, ActionStatus.APPROVED);

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_failCasTransition_when_statusDoesNotMatch() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casTransition(
          action.getId(), ActionStatus.EXECUTING, ActionStatus.SUCCEEDED);

      assertThat(updated).isEqualTo(0);
    }

    @Test
    void should_casApprove_withUserId() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);
      em.flush();

      int updated = casRepository.casApprove(
          action.getId(), ActionStatus.PENDING_APPROVAL, userId);

      assertThat(updated).isEqualTo(1);

      em.clear();
      var refreshed = actionRepository.findById(action.getId()).orElseThrow();
      assertThat(refreshed.getStatus()).isEqualTo(ActionStatus.APPROVED);
      assertThat(refreshed.getApprovedByUserId()).isEqualTo(userId);
      assertThat(refreshed.getApprovedAt()).isNotNull();
    }

    @Test
    void should_casHold_withReason() {
      var action = saveAction(ActionStatus.APPROVED);

      int updated = casRepository.casHold(action.getId(), "Price freeze");

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casCancel_withReason() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casCancel(
          action.getId(), ActionStatus.PENDING_APPROVAL, "User cancelled");

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casRetryScheduled() {
      var action = saveAction(ActionStatus.EXECUTING);

      int updated = casRepository.casRetryScheduled(
          action.getId(), 2, OffsetDateTime.now().plusMinutes(5));

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casSucceed() {
      var action = saveAction(ActionStatus.RECONCILIATION_PENDING);

      int updated = casRepository.casSucceed(
          action.getId(), ActionStatus.RECONCILIATION_PENDING,
          ActionReconciliationSource.AUTO, null);

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casFail() {
      var action = saveAction(ActionStatus.EXECUTING);

      int updated = casRepository.casFail(
          action.getId(), ActionStatus.EXECUTING, 3);

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casIncrementAttempt() {
      var action = saveAction(ActionStatus.EXECUTING);

      int updated = casRepository.casIncrementAttempt(action.getId());

      assertThat(updated).isEqualTo(1);
    }
  }

  private PriceActionEntity saveAction(ActionStatus status) {
    return actionRepository.save(
        aPriceAction()
            .withWorkspaceId(workspaceId)
            .withMarketplaceOfferId(marketplaceOfferId)
            .withPriceDecisionId(priceDecisionId)
            .withStatus(status)
            .build());
  }
}
