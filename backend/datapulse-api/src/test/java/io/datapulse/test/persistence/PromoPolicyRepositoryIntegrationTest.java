package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestConnectionBuilder.aConnection;
import static io.datapulse.test.builder.TestPromoPolicyBuilder.aPromoPolicy;
import static io.datapulse.test.builder.TestSecretReferenceBuilder.aSecretReference;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.canonical.CanonicalPromoCampaignEntity;
import io.datapulse.etl.persistence.canonical.CanonicalPromoProductEntity;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferEntity;
import io.datapulse.etl.persistence.canonical.ProductMasterEntity;
import io.datapulse.etl.persistence.canonical.SellerSkuEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
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
import jakarta.persistence.EntityManager;
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

  @Autowired
  private SecretReferenceRepository secretRefRepository;

  @Autowired
  private MarketplaceConnectionRepository connectionRepository;

  @Autowired
  private JobExecutionRepository jobExecutionRepository;

  @Autowired
  private EntityManager em;

  private Long workspaceId;
  private Long userId;
  private Long promoProductId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    userId = user.getId();
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(userId).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(userId).build());
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
    offer.setMarketplaceSku("test-msku");
    offer.setName("Test Offer");
    offer.setStatus("ACTIVE");
    offer.setJobExecutionId(jobId);
    em.persist(offer);

    var campaign = new CanonicalPromoCampaignEntity();
    campaign.setConnectionId(conn.getId());
    campaign.setExternalPromoId("PROMO-001");
    campaign.setSourcePlatform("WB");
    campaign.setPromoName("Test Promo");
    campaign.setPromoType("SALE");
    campaign.setStatus("ACTIVE");
    campaign.setJobExecutionId(jobId);
    em.persist(campaign);

    var pp = new CanonicalPromoProductEntity();
    pp.setCanonicalPromoCampaignId(campaign.getId());
    pp.setMarketplaceOfferId(offer.getId());
    pp.setParticipationStatus("ELIGIBLE");
    pp.setJobExecutionId(jobId);
    em.persist(pp);
    em.flush();

    promoProductId = pp.getId();
  }

  @Nested
  @DisplayName("PromoPolicyRepository")
  class PolicyTests {

    @Test
    void should_save_and_findByWorkspaceId() {
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).withCreatedBy(userId)
              .withName("PP1").build());
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).withCreatedBy(userId)
              .withName("PP2").build());

      assertThat(policyRepository.findAllByWorkspaceId(workspaceId)).hasSize(2);
    }

    @Test
    void should_filterByStatus() {
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).withCreatedBy(userId)
              .withStatus(PromoPolicyStatus.ACTIVE).build());
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).withCreatedBy(userId)
              .withStatus(PromoPolicyStatus.DRAFT).build());

      assertThat(policyRepository.findAllByWorkspaceIdAndStatus(
          workspaceId, PromoPolicyStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void should_findByIdAndWorkspaceId() {
      var saved = policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).withCreatedBy(userId).build());

      assertThat(policyRepository.findByIdAndWorkspaceId(saved.getId(), workspaceId))
          .isPresent();
      assertThat(policyRepository.findByIdAndWorkspaceId(saved.getId(), 99999L))
          .isEmpty();
    }

    @Test
    void should_existsByWorkspaceIdAndStatus() {
      policyRepository.save(
          aPromoPolicy().withWorkspaceId(workspaceId).withCreatedBy(userId)
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
      d.setCanonicalPromoProductId(promoProductId);
      d.setPolicyVersion(1);
      d.setPolicySnapshot("{}");
      d.setDecisionType(type);
      d.setParticipationMode(ParticipationMode.RECOMMENDATION);
      d.setExecutionMode("RECOMMENDATION");
      return decisionRepository.save(d);
    }
  }
}
