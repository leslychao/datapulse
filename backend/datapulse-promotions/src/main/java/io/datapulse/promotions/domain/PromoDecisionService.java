package io.datapulse.promotions.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.promotions.api.PromoDecisionMapper;
import io.datapulse.promotions.api.PromoDecisionResponse;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoDecisionQueryRepository;
import io.datapulse.promotions.persistence.PromoDecisionRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoDecisionService {

    private final PromoDecisionRepository decisionRepository;
    private final PromoDecisionQueryRepository decisionQueryRepository;
    private final PromoActionRepository actionRepository;
    private final PromoPolicyResolver policyResolver;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PromoDecisionMapper decisionMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PromoDecisionResponse getDecision(long decisionId, long workspaceId) {
        return decisionRepository.findByIdAndWorkspaceId(decisionId, workspaceId)
                .map(decisionMapper::toResponse)
                .orElseThrow(() -> NotFoundException.entity("promo_decision", decisionId));
    }

    @Transactional(readOnly = true)
    public Page<PromoDecisionResponse> listDecisions(long workspaceId, PromoDecisionType decisionType,
                                                      Long campaignId, java.time.LocalDate from,
                                                      java.time.LocalDate to, Pageable pageable) {
        boolean hasExtraFilters = campaignId != null || from != null || to != null;

        if (hasExtraFilters) {
            return decisionQueryRepository.findFiltered(
                    workspaceId, decisionType, campaignId, from, to, pageable)
                    .map(decisionMapper::toResponse);
        }

        Page<PromoDecisionEntity> page = decisionType != null
                ? decisionRepository.findAllByWorkspaceIdAndDecisionType(workspaceId, decisionType, pageable)
                : decisionRepository.findAllByWorkspaceId(workspaceId, pageable);

        return page.map(decisionMapper::toResponse);
    }

    private static final java.util.Set<String> PARTICIPABLE_STATUSES =
            java.util.Set.of("ELIGIBLE", "DECLINED", "AUTO_DECLINED", "REMOVED");

    @Transactional
    public void manualParticipate(long promoProductId, BigDecimal targetPromoPrice,
                                   long workspaceId, long userId) {
        PromoProductInfo info = loadPromoProduct(promoProductId);

        if (!PARTICIPABLE_STATUSES.contains(info.participationStatus())) {
            throw BadRequestException.of(MessageCodes.PROMO_PRODUCT_NOT_ELIGIBLE);
        }

        ensureCampaignNotFrozen(info.campaignId());
        ensureNoActiveAction(promoProductId, info.campaignId());

        PromoPolicyEntity policy = policyResolver.resolvePolicy(
                info.marketplaceOfferId(), info.categoryId(), info.connectionId(), workspaceId);

        PromoPolicySnapshot snapshot = policy != null
                ? PromoPolicySnapshot.from(policy) : PromoPolicySnapshot.empty();

        PromoDecisionEntity decision = new PromoDecisionEntity();
        decision.setWorkspaceId(workspaceId);
        decision.setCanonicalPromoProductId(promoProductId);
        decision.setPolicyVersion(policy != null ? policy.getVersion() : 0);
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(PromoDecisionType.PARTICIPATE);
        decision.setParticipationMode(ParticipationMode.RECOMMENDATION);
        decision.setExecutionMode(PromoExecutionMode.LIVE);
        decision.setTargetPromoPrice(targetPromoPrice != null ? targetPromoPrice : info.requiredPrice());
        decision.setDecidedBy(userId);
        decision.setExplanationSummary("Manual participate by user " + userId);
        decisionRepository.save(decision);

        PromoActionEntity action = new PromoActionEntity();
        action.setWorkspaceId(workspaceId);
        action.setPromoDecisionId(decision.getId());
        action.setCanonicalPromoCampaignId(info.campaignId());
        action.setMarketplaceOfferId(info.marketplaceOfferId());
        action.setActionType(PromoActionType.ACTIVATE);
        action.setTargetPromoPrice(decision.getTargetPromoPrice());
        action.setStatus(PromoActionStatus.APPROVED);
        action.setAttemptCount(0);
        action.setExecutionMode(PromoExecutionMode.LIVE);
        action.setFreezeAtSnapshot(info.freezeAt());
        actionRepository.save(action);

        if (!"ELIGIBLE".equals(info.participationStatus())) {
            updateParticipationStatus(
                    promoProductId, info.participationStatus(), "ELIGIBLE", "MANUAL");
        }

        publishAudit("promo.participate", workspaceId, userId,
                "canonical_promo_product", promoProductId);
        log.info("Manual participate: promoProductId={}, prevStatus={}, actionId={}, userId={}",
                promoProductId, info.participationStatus(), action.getId(), userId);
    }

    @Transactional
    public void manualDecline(long promoProductId, String reason, long workspaceId, long userId) {
        PromoProductInfo info = loadPromoProduct(promoProductId);

        if (!"ELIGIBLE".equals(info.participationStatus())) {
            throw BadRequestException.of(MessageCodes.PROMO_PRODUCT_NOT_ELIGIBLE);
        }

        ensureNoActiveAction(promoProductId, info.campaignId());

        PromoPolicyEntity policy = policyResolver.resolvePolicy(
                info.marketplaceOfferId(), info.categoryId(), info.connectionId(), workspaceId);

        PromoPolicySnapshot snapshot = policy != null
                ? PromoPolicySnapshot.from(policy) : PromoPolicySnapshot.empty();

        PromoDecisionEntity decision = new PromoDecisionEntity();
        decision.setWorkspaceId(workspaceId);
        decision.setCanonicalPromoProductId(promoProductId);
        decision.setPolicyVersion(policy != null ? policy.getVersion() : 0);
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(PromoDecisionType.DECLINE);
        decision.setParticipationMode(ParticipationMode.RECOMMENDATION);
        decision.setExecutionMode(PromoExecutionMode.LIVE);
        decision.setDecidedBy(userId);
        decision.setExplanationSummary(reason != null ? reason : "Manual decline by user " + userId);
        decisionRepository.save(decision);

        updateParticipationStatus(promoProductId, "ELIGIBLE", "DECLINED", "MANUAL");

        publishAudit("promo.decline", workspaceId, userId,
                "canonical_promo_product", promoProductId);
        log.info("Manual decline: promoProductId={}, userId={}", promoProductId, userId);
    }

    @Transactional
    public void manualDeactivate(long promoProductId, String reason, long workspaceId, long userId) {
        PromoProductInfo info = loadPromoProduct(promoProductId);

        if (!"PARTICIPATING".equals(info.participationStatus())) {
            throw BadRequestException.of(MessageCodes.PROMO_PRODUCT_NOT_PARTICIPATING);
        }

        ensureCampaignNotFrozen(info.campaignId());
        cancelActiveActions(promoProductId, info.campaignId());

        PromoPolicyEntity policy = policyResolver.resolvePolicy(
                info.marketplaceOfferId(), info.categoryId(), info.connectionId(), workspaceId);

        PromoPolicySnapshot snapshot = policy != null
                ? PromoPolicySnapshot.from(policy) : PromoPolicySnapshot.empty();

        PromoDecisionEntity decision = new PromoDecisionEntity();
        decision.setWorkspaceId(workspaceId);
        decision.setCanonicalPromoProductId(promoProductId);
        decision.setPolicyVersion(policy != null ? policy.getVersion() : 0);
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(PromoDecisionType.DEACTIVATE);
        decision.setParticipationMode(ParticipationMode.RECOMMENDATION);
        decision.setExecutionMode(PromoExecutionMode.LIVE);
        decision.setDecidedBy(userId);
        decision.setExplanationSummary(reason != null ? reason : "Manual deactivate by user " + userId);
        decisionRepository.save(decision);

        PromoActionEntity action = new PromoActionEntity();
        action.setWorkspaceId(workspaceId);
        action.setPromoDecisionId(decision.getId());
        action.setCanonicalPromoCampaignId(info.campaignId());
        action.setMarketplaceOfferId(info.marketplaceOfferId());
        action.setActionType(PromoActionType.DEACTIVATE);
        action.setStatus(PromoActionStatus.APPROVED);
        action.setAttemptCount(0);
        action.setExecutionMode(PromoExecutionMode.LIVE);
        action.setFreezeAtSnapshot(info.freezeAt());
        actionRepository.save(action);

        publishAudit("promo.deactivate", workspaceId, userId,
                "canonical_promo_product", promoProductId);
        log.info("Manual deactivate: promoProductId={}, actionId={}, userId={}",
                promoProductId, action.getId(), userId);
    }

    private void cancelActiveActions(long promoProductId, long campaignId) {
        var cancelParams = new MapSqlParameterSource()
                .addValue("campaignId", campaignId)
                .addValue("promoProductId", promoProductId);

        int cancelled = jdbcTemplate.update("""
                UPDATE promo_action pa
                SET status = 'CANCELLED',
                    cancel_reason = 'Superseded by manual deactivation',
                    updated_at = NOW()
                FROM promo_decision pd
                WHERE pa.promo_decision_id = pd.id
                  AND pd.canonical_promo_product_id = :promoProductId
                  AND pa.canonical_promo_campaign_id = :campaignId
                  AND pa.status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTING')
                """, cancelParams);

        if (cancelled > 0) {
            log.info("Cancelled {} active actions before deactivation: promoProductId={}, campaignId={}",
                    cancelled, promoProductId, campaignId);
        }
    }

    private void ensureNoActiveAction(long promoProductId, long campaignId) {
        var params = new MapSqlParameterSource()
                .addValue("campaignId", campaignId)
                .addValue("promoProductId", promoProductId);

        Boolean exists = jdbcTemplate.query("""
                SELECT EXISTS(
                  SELECT 1
                  FROM promo_action pa
                  JOIN promo_decision pd ON pa.promo_decision_id = pd.id
                  WHERE pd.canonical_promo_product_id = :promoProductId
                    AND pa.canonical_promo_campaign_id = :campaignId
                    AND pa.status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTING')
                ) AS has_active
                """, params, rs -> {
            rs.next();
            return rs.getBoolean("has_active");
        });

        if (Boolean.TRUE.equals(exists)) {
            throw ConflictException.of(MessageCodes.PROMO_PRODUCT_ACTIVE_ACTION_EXISTS);
        }
    }

    private PromoProductInfo loadPromoProduct(long promoProductId) {
        var params = new MapSqlParameterSource().addValue("id", promoProductId);

        return jdbcTemplate.query("""
                SELECT cpp.id, cpp.canonical_promo_campaign_id, cpp.marketplace_offer_id,
                       cpp.participation_status, cpp.required_price,
                       mo.category_id, cpc.connection_id, cpc.freeze_at
                FROM canonical_promo_product cpp
                JOIN canonical_promo_campaign cpc ON cpp.canonical_promo_campaign_id = cpc.id
                JOIN marketplace_offer mo ON cpp.marketplace_offer_id = mo.id
                WHERE cpp.id = :id
                """, params, rs -> {
            if (!rs.next()) {
                throw NotFoundException.entity("CanonicalPromoProduct", promoProductId);
            }
            return new PromoProductInfo(
                    rs.getLong("id"),
                    rs.getLong("canonical_promo_campaign_id"),
                    rs.getLong("marketplace_offer_id"),
                    rs.getObject("category_id", Long.class),
                    rs.getLong("connection_id"),
                    rs.getString("participation_status"),
                    rs.getBigDecimal("required_price"),
                    rs.getObject("freeze_at", OffsetDateTime.class)
            );
        });
    }

    private void ensureCampaignNotFrozen(long campaignId) {
        var params = new MapSqlParameterSource().addValue("campaignId", campaignId);
        Boolean frozen = jdbcTemplate.query("""
                SELECT freeze_at IS NOT NULL AND freeze_at <= NOW() AS is_frozen
                FROM canonical_promo_campaign WHERE id = :campaignId
                """, params, rs -> {
            if (!rs.next()) return false;
            return rs.getBoolean("is_frozen");
        });

        if (Boolean.TRUE.equals(frozen)) {
            throw BadRequestException.of(MessageCodes.PROMO_CAMPAIGN_FROZEN);
        }
    }

    private void updateParticipationStatus(long promoProductId, String expectedStatus,
                                            String newStatus, String decisionSource) {
        var params = new MapSqlParameterSource()
                .addValue("id", promoProductId)
                .addValue("expectedStatus", expectedStatus)
                .addValue("newStatus", newStatus)
                .addValue("decisionSource", decisionSource);

        int updated = jdbcTemplate.update("""
                UPDATE canonical_promo_product
                SET participation_status = :newStatus,
                    participation_decision_source = :decisionSource,
                    updated_at = NOW()
                WHERE id = :id AND participation_status = :expectedStatus
                """, params);

        if (updated == 0) {
            log.warn("CAS conflict updating canonical_promo_product: id={}, expected={}, target={}",
                    promoProductId, expectedStatus, newStatus);
        }
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    private void publishAudit(String actionType, long workspaceId, long userId,
                               String entityType, long entityId) {
        eventPublisher.publishEvent(new AuditEvent(
                workspaceId, "USER", userId, actionType,
                entityType, String.valueOf(entityId),
                "SUCCESS", null, null, null));
    }

    private record PromoProductInfo(long id, long campaignId, long marketplaceOfferId,
                                     Long categoryId, long connectionId,
                                     String participationStatus, BigDecimal requiredPrice,
                                     OffsetDateTime freezeAt) {
    }
}
