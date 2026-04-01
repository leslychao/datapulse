package io.datapulse.promotions.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.PromoDecisionMapper;
import io.datapulse.promotions.api.PromoDecisionResponse;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoDecisionRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PromoActionRepository actionRepository;
    private final PromoPolicyResolver policyResolver;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PromoDecisionMapper decisionMapper;

    @Transactional(readOnly = true)
    public Page<PromoDecisionResponse> listDecisions(long workspaceId, PromoDecisionType decisionType,
                                                      Pageable pageable) {
        Page<PromoDecisionEntity> page = decisionType != null
                ? decisionRepository.findAllByWorkspaceIdAndDecisionType(workspaceId, decisionType, pageable)
                : decisionRepository.findAllByWorkspaceId(workspaceId, pageable);

        return page.map(decisionMapper::toResponse);
    }

    @Transactional
    public void manualParticipate(long promoProductId, BigDecimal targetPromoPrice,
                                   long workspaceId, long userId) {
        PromoProductInfo info = loadPromoProduct(promoProductId);

        if (!"ELIGIBLE".equals(info.participationStatus())) {
            throw BadRequestException.of(MessageCodes.PROMO_PRODUCT_NOT_ELIGIBLE);
        }

        ensureCampaignNotFrozen(info.campaignId());

        PromoPolicyEntity policy = policyResolver.resolvePolicy(
                info.marketplaceOfferId(), info.categoryId(), info.connectionId(), workspaceId);

        PromoPolicySnapshot snapshot = policy != null
                ? buildSnapshot(policy)
                : new PromoPolicySnapshot(null, 0, null, ParticipationMode.RECOMMENDATION,
                BigDecimal.ZERO, 7, null, null, null, null);

        PromoDecisionEntity decision = new PromoDecisionEntity();
        decision.setWorkspaceId(workspaceId);
        decision.setCanonicalPromoProductId(promoProductId);
        decision.setPolicyVersion(policy != null ? policy.getVersion() : 0);
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(PromoDecisionType.PARTICIPATE);
        decision.setParticipationMode(ParticipationMode.RECOMMENDATION);
        decision.setExecutionMode("LIVE");
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

        log.info("Manual participate: promoProductId={}, actionId={}, userId={}",
                promoProductId, action.getId(), userId);
    }

    @Transactional
    public void manualDecline(long promoProductId, String reason, long workspaceId, long userId) {
        PromoProductInfo info = loadPromoProduct(promoProductId);

        if (!"ELIGIBLE".equals(info.participationStatus())) {
            throw BadRequestException.of(MessageCodes.PROMO_PRODUCT_NOT_ELIGIBLE);
        }

        PromoPolicyEntity policy = policyResolver.resolvePolicy(
                info.marketplaceOfferId(), info.categoryId(), info.connectionId(), workspaceId);

        PromoPolicySnapshot snapshot = policy != null
                ? buildSnapshot(policy)
                : new PromoPolicySnapshot(null, 0, null, ParticipationMode.RECOMMENDATION,
                BigDecimal.ZERO, 7, null, null, null, null);

        PromoDecisionEntity decision = new PromoDecisionEntity();
        decision.setWorkspaceId(workspaceId);
        decision.setCanonicalPromoProductId(promoProductId);
        decision.setPolicyVersion(policy != null ? policy.getVersion() : 0);
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(PromoDecisionType.DECLINE);
        decision.setParticipationMode(ParticipationMode.RECOMMENDATION);
        decision.setExecutionMode("LIVE");
        decision.setDecidedBy(userId);
        decision.setExplanationSummary(reason != null ? reason : "Manual decline by user " + userId);
        decisionRepository.save(decision);

        updateParticipationStatus(promoProductId, "DECLINED", "MANUAL");

        log.info("Manual decline: promoProductId={}, userId={}", promoProductId, userId);
    }

    @Transactional
    public void manualDeactivate(long promoProductId, String reason, long workspaceId, long userId) {
        PromoProductInfo info = loadPromoProduct(promoProductId);

        if (!"PARTICIPATING".equals(info.participationStatus())) {
            throw BadRequestException.of(MessageCodes.PROMO_PRODUCT_NOT_PARTICIPATING);
        }

        ensureCampaignNotFrozen(info.campaignId());

        PromoPolicyEntity policy = policyResolver.resolvePolicy(
                info.marketplaceOfferId(), info.categoryId(), info.connectionId(), workspaceId);

        PromoPolicySnapshot snapshot = policy != null
                ? buildSnapshot(policy)
                : new PromoPolicySnapshot(null, 0, null, ParticipationMode.RECOMMENDATION,
                BigDecimal.ZERO, 7, null, null, null, null);

        PromoDecisionEntity decision = new PromoDecisionEntity();
        decision.setWorkspaceId(workspaceId);
        decision.setCanonicalPromoProductId(promoProductId);
        decision.setPolicyVersion(policy != null ? policy.getVersion() : 0);
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(PromoDecisionType.DEACTIVATE);
        decision.setParticipationMode(ParticipationMode.RECOMMENDATION);
        decision.setExecutionMode("LIVE");
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

        log.info("Manual deactivate: promoProductId={}, actionId={}, userId={}",
                promoProductId, action.getId(), userId);
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

    private void updateParticipationStatus(long promoProductId, String status, String decisionSource) {
        var params = new MapSqlParameterSource()
                .addValue("id", promoProductId)
                .addValue("status", status)
                .addValue("decisionSource", decisionSource);

        jdbcTemplate.update("""
                UPDATE canonical_promo_product
                SET participation_status = :status,
                    participation_decision_source = :decisionSource,
                    updated_at = NOW()
                WHERE id = :id
                """, params);
    }

    private PromoPolicySnapshot buildSnapshot(PromoPolicyEntity policy) {
        return new PromoPolicySnapshot(
                policy.getId(), policy.getVersion(), policy.getName(),
                policy.getParticipationMode(), policy.getMinMarginPct(),
                policy.getMinStockDaysOfCover(), policy.getMaxPromoDiscountPct(),
                policy.getAutoParticipateCategories(), policy.getAutoDeclineCategories(),
                policy.getEvaluationConfig());
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    private record PromoProductInfo(long id, long campaignId, long marketplaceOfferId,
                                     Long categoryId, long connectionId,
                                     String participationStatus, BigDecimal requiredPrice,
                                     OffsetDateTime freezeAt) {
    }
}
