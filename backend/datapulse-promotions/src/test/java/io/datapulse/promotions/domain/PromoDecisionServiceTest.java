package io.datapulse.promotions.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.promotions.api.PromoDecisionMapper;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoDecisionQueryRepository;
import io.datapulse.promotions.persistence.PromoDecisionRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.datapulse.platform.audit.AuditPublisher;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoDecisionServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long USER_ID = 10L;
  private static final long PROMO_PRODUCT_ID = 100L;

  @Mock
  private PromoDecisionRepository decisionRepository;
  @Mock
  private PromoDecisionQueryRepository decisionQueryRepository;
  @Mock
  private PromoActionRepository actionRepository;
  @Mock
  private PromoPolicyResolver policyResolver;
  @Mock
  private NamedParameterJdbcTemplate jdbcTemplate;
  @Mock
  private ObjectMapper objectMapper;
  @Mock
  private PromoDecisionMapper decisionMapper;
  @Mock
  private AuditPublisher auditPublisher;

  @InjectMocks
  private PromoDecisionService service;

  @Nested
  @DisplayName("manualParticipate")
  class ManualParticipate {

    @Test
    @SuppressWarnings("unchecked")
    void should_create_decision_and_action_when_eligible() throws Exception {
      mockJdbcForProduct("ELIGIBLE", false);

      when(policyResolver.resolvePolicy(eq(1L), any(), eq(5L), eq(WORKSPACE_ID)))
          .thenReturn(buildPolicy());
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.manualParticipate(PROMO_PRODUCT_ID, new BigDecimal("450"), WORKSPACE_ID, USER_ID);

      ArgumentCaptor<PromoDecisionEntity> decCaptor =
          ArgumentCaptor.forClass(PromoDecisionEntity.class);
      verify(decisionRepository).save(decCaptor.capture());
      assertThat(decCaptor.getValue().getDecisionType()).isEqualTo(PromoDecisionType.PARTICIPATE);
      assertThat(decCaptor.getValue().getDecidedBy()).isEqualTo(USER_ID);

      ArgumentCaptor<PromoActionEntity> actCaptor =
          ArgumentCaptor.forClass(PromoActionEntity.class);
      verify(actionRepository).save(actCaptor.capture());
      assertThat(actCaptor.getValue().getActionType()).isEqualTo(PromoActionType.ACTIVATE);
      assertThat(actCaptor.getValue().getStatus()).isEqualTo(PromoActionStatus.APPROVED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throw_when_product_not_eligible() throws Exception {
      mockJdbcForProduct("PARTICIPATING", false);

      assertThatThrownBy(() ->
          service.manualParticipate(PROMO_PRODUCT_ID, null, WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throw_when_campaign_frozen() throws Exception {
      mockJdbcForProduct("ELIGIBLE", true);

      assertThatThrownBy(() ->
          service.manualParticipate(PROMO_PRODUCT_ID, null, WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_use_required_price_when_target_not_provided() throws Exception {
      mockJdbcForProduct("ELIGIBLE", false);

      when(policyResolver.resolvePolicy(eq(1L), any(), eq(5L), eq(WORKSPACE_ID)))
          .thenReturn(buildPolicy());
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.manualParticipate(PROMO_PRODUCT_ID, null, WORKSPACE_ID, USER_ID);

      ArgumentCaptor<PromoDecisionEntity> captor =
          ArgumentCaptor.forClass(PromoDecisionEntity.class);
      verify(decisionRepository).save(captor.capture());
      assertThat(captor.getValue().getTargetPromoPrice())
          .isEqualByComparingTo(new BigDecimal("500"));
    }
  }

  @Nested
  @DisplayName("manualDecline")
  class ManualDecline {

    @Test
    @SuppressWarnings("unchecked")
    void should_create_decline_decision_when_eligible() throws Exception {
      mockJdbcForProduct("ELIGIBLE", false);

      when(policyResolver.resolvePolicy(eq(1L), any(), eq(5L), eq(WORKSPACE_ID)))
          .thenReturn(buildPolicy());
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

      service.manualDecline(PROMO_PRODUCT_ID, "Not profitable", WORKSPACE_ID, USER_ID);

      ArgumentCaptor<PromoDecisionEntity> captor =
          ArgumentCaptor.forClass(PromoDecisionEntity.class);
      verify(decisionRepository).save(captor.capture());
      assertThat(captor.getValue().getDecisionType()).isEqualTo(PromoDecisionType.DECLINE);
      assertThat(captor.getValue().getExplanationSummary()).isEqualTo("Not profitable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throw_when_product_not_eligible() throws Exception {
      mockJdbcForProduct("DECLINED", false);

      assertThatThrownBy(() ->
          service.manualDecline(PROMO_PRODUCT_ID, "reason", WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("manualDeactivate")
  class ManualDeactivate {

    @Test
    @SuppressWarnings("unchecked")
    void should_create_deactivate_action_when_participating() throws Exception {
      mockJdbcForProduct("PARTICIPATING", false);

      when(policyResolver.resolvePolicy(eq(1L), any(), eq(5L), eq(WORKSPACE_ID)))
          .thenReturn(buildPolicy());
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.manualDeactivate(PROMO_PRODUCT_ID, "Margin too low", WORKSPACE_ID, USER_ID);

      ArgumentCaptor<PromoDecisionEntity> decCaptor =
          ArgumentCaptor.forClass(PromoDecisionEntity.class);
      verify(decisionRepository).save(decCaptor.capture());
      assertThat(decCaptor.getValue().getDecisionType()).isEqualTo(PromoDecisionType.DEACTIVATE);

      ArgumentCaptor<PromoActionEntity> actCaptor =
          ArgumentCaptor.forClass(PromoActionEntity.class);
      verify(actionRepository).save(actCaptor.capture());
      assertThat(actCaptor.getValue().getActionType()).isEqualTo(PromoActionType.DEACTIVATE);
      assertThat(actCaptor.getValue().getStatus()).isEqualTo(PromoActionStatus.APPROVED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throw_when_product_not_participating() throws Exception {
      mockJdbcForProduct("ELIGIBLE", false);

      assertThatThrownBy(() ->
          service.manualDeactivate(PROMO_PRODUCT_ID, "reason", WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @SuppressWarnings("unchecked")
  private void mockJdbcForProduct(String participationStatus, boolean frozen)
      throws SQLException {
    when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class),
        any(ResultSetExtractor.class)))
        .thenAnswer(inv -> {
          String sql = inv.getArgument(0);
          ResultSetExtractor<?> extractor = inv.getArgument(2);

          if (sql.contains("canonical_promo_product cpp")) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getLong("id")).thenReturn(PROMO_PRODUCT_ID);
            when(rs.getLong("canonical_promo_campaign_id")).thenReturn(1L);
            when(rs.getLong("marketplace_offer_id")).thenReturn(1L);
            when(rs.getObject("category_id", Long.class)).thenReturn(10L);
            when(rs.getLong("connection_id")).thenReturn(5L);
            when(rs.getString("participation_status")).thenReturn(participationStatus);
            when(rs.getBigDecimal("required_price")).thenReturn(new BigDecimal("500"));
            when(rs.getObject("freeze_at", OffsetDateTime.class)).thenReturn(null);
            return extractor.extractData(rs);
          }
          if (sql.contains("freeze_at")) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getBoolean("is_frozen")).thenReturn(frozen);
            return extractor.extractData(rs);
          }
          return null;
        });
  }

  private PromoPolicyEntity buildPolicy() {
    var p = new PromoPolicyEntity();
    p.setId(1L);
    p.setWorkspaceId(WORKSPACE_ID);
    p.setStatus(PromoPolicyStatus.ACTIVE);
    p.setParticipationMode(ParticipationMode.RECOMMENDATION);
    p.setMinMarginPct(new BigDecimal("10"));
    p.setMinStockDaysOfCover(7);
    p.setVersion(1);
    p.setCreatedBy(1L);
    p.setName("Test");
    return p;
  }
}
