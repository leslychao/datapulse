package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoActionStatus;
import io.datapulse.promotions.domain.PromoActionType;
import io.datapulse.promotions.domain.PromoExecutionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PromoActionQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<PromoActionEntity> findFiltered(long workspaceId, Long campaignId,
                                                  PromoActionStatus status,
                                                  PromoActionType actionType,
                                                  Pageable pageable) {
        var where = new StringBuilder("""
                SELECT pa.* FROM promo_action pa
                WHERE pa.workspace_id = :workspaceId
                """);
        var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);

        if (campaignId != null) {
            where.append(" AND pa.canonical_promo_campaign_id = :campaignId");
            params.addValue("campaignId", campaignId);
        }
        if (status != null) {
            where.append(" AND pa.status = :status");
            params.addValue("status", status.name());
        }
        if (actionType != null) {
            where.append(" AND pa.action_type = :actionType");
            params.addValue("actionType", actionType.name());
        }

        var countSql = "SELECT count(*) FROM (" + where + ") sub";
        int total = Optional.ofNullable(jdbcTemplate.queryForObject(countSql, params, Integer.class))
                .orElse(0);

        where.append(" ORDER BY pa.created_at DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PromoActionEntity> content = jdbcTemplate.query(
                where.toString(), params, (rs, rowNum) -> {
                    var entity = new PromoActionEntity();
                    entity.setId(rs.getLong("id"));
                    entity.setWorkspaceId(rs.getLong("workspace_id"));
                    entity.setPromoDecisionId(rs.getLong("promo_decision_id"));
                    entity.setCanonicalPromoCampaignId(rs.getLong("canonical_promo_campaign_id"));
                    entity.setMarketplaceOfferId(rs.getLong("marketplace_offer_id"));
                    entity.setActionType(PromoActionType.valueOf(rs.getString("action_type")));
                    entity.setTargetPromoPrice(rs.getBigDecimal("target_promo_price"));
                    entity.setStatus(PromoActionStatus.valueOf(rs.getString("status")));
                    entity.setAttemptCount(rs.getInt("attempt_count"));
                    entity.setLastError(rs.getString("last_error"));
                    entity.setExecutionMode(PromoExecutionMode.valueOf(rs.getString("execution_mode")));
                    entity.setFreezeAtSnapshot(rs.getObject("freeze_at_snapshot", OffsetDateTime.class));
                    entity.setCancelReason(rs.getString("cancel_reason"));
                    return entity;
                });

        return new PageImpl<>(content, pageable, total);
    }
}
