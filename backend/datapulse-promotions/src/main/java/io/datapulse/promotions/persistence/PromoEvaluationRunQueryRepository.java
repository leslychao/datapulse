package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoRunStatus;
import io.datapulse.promotions.domain.PromoRunTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PromoEvaluationRunQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String RESOLVE_CONNECTION_SQL = """
            SELECT id FROM marketplace_connection
            WHERE workspace_id = :workspaceId
              AND marketplace_type = :sourcePlatform
              AND status <> 'ARCHIVED'
            """;

    private static final String CONNECTION_MARKETPLACE_TYPES_SQL = """
            SELECT id, marketplace_type FROM marketplace_connection WHERE id IN (:ids)
            """;

    public Map<Long, String> findConnectionMarketplaceTypes(Collection<Long> connectionIds) {
        if (connectionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbcTemplate.query(CONNECTION_MARKETPLACE_TYPES_SQL,
                Map.of("ids", connectionIds),
                (rs, rowNum) -> Map.entry(
                    rs.getLong("id"), rs.getString("marketplace_type")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Long resolveConnectionId(long workspaceId, String sourcePlatform) {
        if (sourcePlatform == null) {
            return null;
        }
        var params = Map.of("workspaceId", workspaceId, "sourcePlatform", sourcePlatform);
        List<Long> ids = jdbcTemplate.queryForList(RESOLVE_CONNECTION_SQL, params, Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Page<PromoEvaluationRunEntity> findFiltered(long workspaceId, Long connectionId,
                                                        PromoRunStatus status,
                                                        LocalDate from, LocalDate to,
                                                        Pageable pageable) {
        var where = new StringBuilder("""
                SELECT r.* FROM promo_evaluation_run r
                WHERE r.workspace_id = :workspaceId
                """);
        var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);

        if (connectionId != null) {
            where.append(" AND r.connection_id = :connectionId");
            params.addValue("connectionId", connectionId);
        }
        if (status != null) {
            where.append(" AND r.status = :status");
            params.addValue("status", status.name());
        }
        if (from != null) {
            where.append(" AND r.created_at >= :from");
            params.addValue("from", from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND r.created_at < :toExclusive");
            params.addValue("toExclusive", to.plusDays(1).atStartOfDay());
        }

        var countSql = "SELECT count(*) FROM (" + where + ") sub";
        int total = Optional.ofNullable(
                jdbcTemplate.queryForObject(countSql, params, Integer.class)).orElse(0);

        where.append(" ORDER BY r.created_at DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PromoEvaluationRunEntity> content = jdbcTemplate.query(
                where.toString(), params, (rs, rowNum) -> {
                    var entity = new PromoEvaluationRunEntity();
                    entity.setId(rs.getLong("id"));
                    entity.setWorkspaceId(rs.getLong("workspace_id"));
                    entity.setConnectionId(rs.getLong("connection_id"));
                    entity.setTriggerType(
                            PromoRunTriggerType.valueOf(rs.getString("trigger_type")));
                    entity.setSourceJobExecutionId(
                            rs.getObject("source_job_execution_id", Long.class));
                    entity.setStatus(PromoRunStatus.valueOf(rs.getString("status")));
                    entity.setTotalProducts(
                            rs.getObject("total_products", Integer.class));
                    entity.setEligibleCount(
                            rs.getObject("eligible_count", Integer.class));
                    entity.setParticipateCount(
                            rs.getObject("participate_count", Integer.class));
                    entity.setDeclineCount(
                            rs.getObject("decline_count", Integer.class));
                    entity.setPendingReviewCount(
                            rs.getObject("pending_review_count", Integer.class));
                    entity.setDeactivateCount(
                            rs.getObject("deactivate_count", Integer.class));
                    entity.setStartedAt(
                            rs.getObject("started_at", OffsetDateTime.class));
                    entity.setCompletedAt(
                            rs.getObject("completed_at", OffsetDateTime.class));
                    entity.setCreatedAt(
                            rs.getObject("created_at", OffsetDateTime.class));
                    return entity;
                });

        return new PageImpl<>(content, pageable, total);
    }
}
