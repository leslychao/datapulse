package io.datapulse.test;

import static io.datapulse.test.builder.TestConnectionBuilder.aConnection;
import static io.datapulse.test.builder.TestSecretReferenceBuilder.aSecretReference;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.canonical.CostProfileEntity;
import io.datapulse.etl.persistence.canonical.CostProfileRepository;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferEntity;
import io.datapulse.etl.persistence.canonical.ProductMasterEntity;
import io.datapulse.etl.persistence.canonical.SellerSkuEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import io.datapulse.pricing.api.BulkManualApplyResponse;
import io.datapulse.pricing.api.BulkManualPreviewRequest;
import io.datapulse.pricing.api.BulkManualPreviewRequest.PriceChange;
import io.datapulse.pricing.api.BulkManualPreviewResponse;
import io.datapulse.pricing.domain.BulkManualPricingService;
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
import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

@DisplayName("Bulk Manual Pricing — integration (Testcontainers)")
class BulkManualPricingIntegrationTest extends AbstractIntegrationTest {

  @Autowired private BulkManualPricingService bulkService;
  @Autowired private PricingRunRepository runRepository;
  @Autowired private PriceDecisionRepository decisionRepository;
  @Autowired private CostProfileRepository costProfileRepository;

  @Autowired private AppUserRepository appUserRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private WorkspaceRepository workspaceRepository;
  @Autowired private SecretReferenceRepository secretRefRepository;
  @Autowired private MarketplaceConnectionRepository connectionRepository;
  @Autowired private JobExecutionRepository jobExecutionRepository;

  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private Long workspaceId;
  private Long userId;
  private Long connectionId;
  private Long jobId;
  private Long offerIdA;
  private Long offerIdB;

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
    connectionId = conn.getId();

    jobId = jobExecutionRepository.insert(conn.getId(), "FULL_SYNC");

    var pm = new ProductMasterEntity();
    pm.setWorkspaceId(workspaceId);
    pm.setExternalCode("ext-001");
    pm.setName("Test Product A");
    pm.setJobExecutionId(jobId);
    em.persist(pm);

    var skuA = new SellerSkuEntity();
    skuA.setProductMasterId(pm.getId());
    skuA.setSkuCode("SKU-A");
    skuA.setJobExecutionId(jobId);
    em.persist(skuA);

    var skuB = new SellerSkuEntity();
    skuB.setProductMasterId(pm.getId());
    skuB.setSkuCode("SKU-B");
    skuB.setJobExecutionId(jobId);
    em.persist(skuB);

    var offerA = new MarketplaceOfferEntity();
    offerA.setSellerSkuId(skuA.getId());
    offerA.setMarketplaceConnectionId(conn.getId());
    offerA.setMarketplaceType("WB");
    offerA.setMarketplaceSku("msku-a");
    offerA.setName("Offer A");
    offerA.setStatus("ACTIVE");
    offerA.setJobExecutionId(jobId);
    em.persist(offerA);

    var offerB = new MarketplaceOfferEntity();
    offerB.setSellerSkuId(skuB.getId());
    offerB.setMarketplaceConnectionId(conn.getId());
    offerB.setMarketplaceType("WB");
    offerB.setMarketplaceSku("msku-b");
    offerB.setName("Offer B");
    offerB.setStatus("ACTIVE");
    offerB.setJobExecutionId(jobId);
    em.persist(offerB);
    em.flush();

    offerIdA = offerA.getId();
    offerIdB = offerB.getId();

    insertCanonicalPrice(offerIdA, new BigDecimal("1000"), null, null);
    insertCanonicalPrice(offerIdB, new BigDecimal("2000"), null, null);

    insertCostProfile(skuA.getId(), new BigDecimal("500"), LocalDate.now().minusDays(30));
    insertCostProfile(skuB.getId(), new BigDecimal("800"), LocalDate.now().minusDays(30));

    insertSyncState(conn.getId(), OffsetDateTime.now().minusHours(2));
  }

  @Nested
  @DisplayName("preview")
  class Preview {

    @Test
    @DisplayName("should_returnPreviewWithSummary_when_validChanges")
    void should_returnPreviewWithSummary_when_validChanges() {
      var request = new BulkManualPreviewRequest(List.of(
          new PriceChange(offerIdA, new BigDecimal("1200")),
          new PriceChange(offerIdB, new BigDecimal("2500"))));

      BulkManualPreviewResponse response = bulkService.preview(request, workspaceId);

      assertThat(response.summary()).isNotNull();
      assertThat(response.summary().totalRequested()).isEqualTo(2);
      assertThat(response.offers()).hasSize(2);
    }

    @Test
    @DisplayName("should_skipOffer_when_notFound")
    void should_skipOffer_when_notFound() {
      var request = new BulkManualPreviewRequest(List.of(
          new PriceChange(99999L, new BigDecimal("1200"))));

      BulkManualPreviewResponse response = bulkService.preview(request, workspaceId);

      assertThat(response.offers()).hasSize(1);
      assertThat(response.offers().get(0).result()).isEqualTo("SKIP");
      assertThat(response.summary().willSkip()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("apply")
  class Apply {

    @Test
    @DisplayName("should_createRunAndDecisions_when_validApply")
    void should_createRunAndDecisions_when_validApply() {
      var request = new BulkManualPreviewRequest(List.of(
          new PriceChange(offerIdA, new BigDecimal("1200")),
          new PriceChange(offerIdB, new BigDecimal("2500"))));

      BulkManualApplyResponse response = bulkService.apply(request, workspaceId, userId);

      assertThat(response.pricingRunId()).isNotNull();
      assertThat(response.processed()).isGreaterThan(0);

      PricingRunEntity run = runRepository.findById(response.pricingRunId()).orElse(null);
      assertThat(run).isNotNull();
      assertThat(run.getTriggerType()).isEqualTo(RunTriggerType.MANUAL_BULK);
      assertThat(run.getStatus()).isIn(RunStatus.COMPLETED, RunStatus.COMPLETED_WITH_ERRORS);
      assertThat(run.getRequestHash()).isNotBlank();

      List<PriceDecisionEntity> decisions =
          decisionRepository.findAllByPricingRunId(
              response.pricingRunId(), PageRequest.of(0, 100)).getContent();
      assertThat(decisions).isNotEmpty();
      assertThat(decisions).allSatisfy(d -> {
        assertThat(d.getStrategyType()).isEqualTo(PolicyType.MANUAL_OVERRIDE);
        assertThat(d.getDecisionType()).isEqualTo(DecisionType.CHANGE);
        assertThat(d.getExecutionMode()).isEqualTo("LIVE");
      });
    }

    @Test
    @DisplayName("should_return409_when_duplicateRequest")
    void should_return409_when_duplicateRequest() {
      var request = new BulkManualPreviewRequest(List.of(
          new PriceChange(offerIdA, new BigDecimal("1200"))));

      bulkService.apply(request, workspaceId, userId);

      assertThatThrownBy(() -> bulkService.apply(request, workspaceId, userId))
          .isInstanceOf(BadRequestException.class);
    }
  }

  private void insertCanonicalPrice(long offerId, BigDecimal price,
                                     BigDecimal discountPrice, BigDecimal minPrice) {
    jdbc.update("""
        INSERT INTO canonical_price_current
            (marketplace_offer_id, price, discount_price, min_price,
             job_execution_id, captured_at)
        VALUES (:offerId, :price, :discountPrice, :minPrice,
                :jobId, now())
        ON CONFLICT (marketplace_offer_id) DO UPDATE SET
            price = EXCLUDED.price,
            discount_price = EXCLUDED.discount_price,
            min_price = EXCLUDED.min_price
        """, Map.of(
        "offerId", offerId,
        "price", price,
        "discountPrice", discountPrice != null ? discountPrice : price,
        "minPrice", minPrice != null ? minPrice : BigDecimal.ZERO,
        "jobId", jobId));
  }

  private void insertCostProfile(long sellerSkuId, BigDecimal costPrice, LocalDate validFrom) {
    var entity = new CostProfileEntity();
    entity.setSellerSkuId(sellerSkuId);
    entity.setCostPrice(costPrice);
    entity.setCurrency("RUB");
    entity.setValidFrom(validFrom);
    entity.setUpdatedByUserId(userId);
    costProfileRepository.createVersion(entity);
  }

  private void insertSyncState(long connectionId, OffsetDateTime lastSuccessAt) {
    jdbc.update("""
        INSERT INTO marketplace_sync_state
            (marketplace_connection_id, data_domain, last_success_at, status)
        VALUES (:connectionId, 'FINANCE', :lastSuccessAt, 'COMPLETED')
        ON CONFLICT (marketplace_connection_id, data_domain) DO UPDATE SET
            last_success_at = EXCLUDED.last_success_at
        """, Map.of(
        "connectionId", connectionId,
        "lastSuccessAt", lastSuccessAt));
  }
}
