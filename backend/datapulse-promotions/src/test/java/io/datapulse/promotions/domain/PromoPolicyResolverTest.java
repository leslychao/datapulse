package io.datapulse.promotions.domain;

import io.datapulse.promotions.persistence.PromoPolicyAssignmentEntity;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoPolicyResolverTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long CONNECTION_ID = 5L;
  private static final long OFFER_ID = 100L;
  private static final long CATEGORY_ID = 10L;

  @Mock
  private PromoPolicyRepository policyRepository;
  @Mock
  private PromoPolicyAssignmentRepository assignmentRepository;
  @Mock
  private NamedParameterJdbcTemplate jdbcTemplate;

  @InjectMocks
  private PromoPolicyResolver resolver;

  @Nested
  @DisplayName("resolvePolicy")
  class ResolvePolicy {

    @Test
    void should_return_null_when_no_assignments() {
      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of());

      PromoPolicyEntity result = resolver.resolvePolicy(
          OFFER_ID, CATEGORY_ID, CONNECTION_ID, WORKSPACE_ID);

      assertThat(result).isNull();
    }

    @Test
    void should_prefer_sku_over_category_over_connection() {
      var skuPolicy = buildPolicy(1L, "SKU Policy");
      var catPolicy = buildPolicy(2L, "Category Policy");
      var connPolicy = buildPolicy(3L, "Connection Policy");

      var skuAssignment = buildAssignment(1L, PromoScopeType.SKU);
      skuAssignment.setMarketplaceOfferId(OFFER_ID);
      var catAssignment = buildAssignment(2L, PromoScopeType.CATEGORY);
      catAssignment.setCategoryId(CATEGORY_ID);
      var connAssignment = buildAssignment(3L, PromoScopeType.CONNECTION);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssignment, catAssignment, skuAssignment));
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE))
          .thenReturn(List.of(skuPolicy, catPolicy, connPolicy));

      PromoPolicyEntity result = resolver.resolvePolicy(
          OFFER_ID, CATEGORY_ID, CONNECTION_ID, WORKSPACE_ID);

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getName()).isEqualTo("SKU Policy");
    }

    @Test
    void should_prefer_category_over_connection() {
      var catPolicy = buildPolicy(2L, "Category Policy");
      var connPolicy = buildPolicy(3L, "Connection Policy");

      var catAssignment = buildAssignment(2L, PromoScopeType.CATEGORY);
      catAssignment.setCategoryId(CATEGORY_ID);
      var connAssignment = buildAssignment(3L, PromoScopeType.CONNECTION);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssignment, catAssignment));
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE))
          .thenReturn(List.of(catPolicy, connPolicy));

      PromoPolicyEntity result = resolver.resolvePolicy(
          OFFER_ID, CATEGORY_ID, CONNECTION_ID, WORKSPACE_ID);

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("Category Policy");
    }

    @Test
    void should_fallback_to_connection_scope() {
      var connPolicy = buildPolicy(3L, "Connection Policy");
      var connAssignment = buildAssignment(3L, PromoScopeType.CONNECTION);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssignment));
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE))
          .thenReturn(List.of(connPolicy));

      PromoPolicyEntity result = resolver.resolvePolicy(
          OFFER_ID, CATEGORY_ID, CONNECTION_ID, WORKSPACE_ID);

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("Connection Policy");
    }

    @Test
    void should_skip_sku_assignment_when_offer_does_not_match() {
      var skuPolicy = buildPolicy(1L, "SKU Policy");
      var connPolicy = buildPolicy(3L, "Connection Policy");

      var skuAssignment = buildAssignment(1L, PromoScopeType.SKU);
      skuAssignment.setMarketplaceOfferId(999L);
      var connAssignment = buildAssignment(3L, PromoScopeType.CONNECTION);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(skuAssignment, connAssignment));
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE))
          .thenReturn(List.of(skuPolicy, connPolicy));

      PromoPolicyEntity result = resolver.resolvePolicy(
          OFFER_ID, CATEGORY_ID, CONNECTION_ID, WORKSPACE_ID);

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("Connection Policy");
    }

    @Test
    void should_return_null_when_assigned_policy_is_not_active() {
      var connAssignment = buildAssignment(3L, PromoScopeType.CONNECTION);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssignment));
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE))
          .thenReturn(List.of());

      PromoPolicyEntity result = resolver.resolvePolicy(
          OFFER_ID, CATEGORY_ID, CONNECTION_ID, WORKSPACE_ID);

      assertThat(result).isNull();
    }
  }

  private PromoPolicyEntity buildPolicy(long id, String name) {
    var p = new PromoPolicyEntity();
    p.setId(id);
    p.setWorkspaceId(WORKSPACE_ID);
    p.setName(name);
    p.setStatus(PromoPolicyStatus.ACTIVE);
    p.setParticipationMode(ParticipationMode.FULL_AUTO);
    p.setMinMarginPct(BigDecimal.TEN);
    p.setMinStockDaysOfCover(7);
    p.setVersion(1);
    p.setCreatedBy(1L);
    return p;
  }

  private PromoPolicyAssignmentEntity buildAssignment(long policyId, PromoScopeType scope) {
    var a = new PromoPolicyAssignmentEntity();
    a.setPromoPolicyId(policyId);
    a.setMarketplaceConnectionId(CONNECTION_ID);
    a.setScopeType(scope);
    return a;
  }
}
