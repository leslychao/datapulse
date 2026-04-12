package io.datapulse.pricing.persistence;

import io.datapulse.pricing.api.PricingRunFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PricingRunReadRepository {

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;

    public Page<PricingRunEntity> findByFilter(long workspaceId, PricingRunFilter filter,
                                               Pageable pageable) {
        var where = new StringBuilder("WHERE r.workspaceId = :workspaceId");
        Map<String, Object> params = new HashMap<>();
        params.put("workspaceId", workspaceId);

        appendFilters(filter, where, params);

        String countJpql = "SELECT COUNT(r) FROM PricingRunEntity r " + where;
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        String dataJpql = "SELECT r FROM PricingRunEntity r " + where
                + " ORDER BY r.createdAt DESC";
        TypedQuery<PricingRunEntity> dataQuery = entityManager.createQuery(
                dataJpql, PricingRunEntity.class);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(dataQuery.getResultList(), pageable, total);
    }

    private static final String CONNECTION_NAMES_SQL = """
            SELECT id, name FROM marketplace_connection WHERE id IN (:ids)
            """;

    public Map<Long, String> findConnectionNames(Collection<Long> connectionIds) {
        if (connectionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(CONNECTION_NAMES_SQL,
                Map.of("ids", connectionIds),
                (rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("name")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static final String OFFER_INFO_SQL = """
            SELECT id, name, marketplace_sku, marketplace_connection_id
            FROM marketplace_offer WHERE id IN (:ids)
            """;

    public Map<Long, OfferInfo> findOfferInfo(Collection<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(OFFER_INFO_SQL,
                Map.of("ids", offerIds),
                (rs, rowNum) -> Map.entry(rs.getLong("id"),
                    new OfferInfo(
                        rs.getString("name"),
                        rs.getString("marketplace_sku"),
                        rs.getLong("marketplace_connection_id"))))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static final String POLICY_NAMES_SQL = """
            SELECT id, name FROM price_policy WHERE id IN (:ids)
            """;

    public Map<Long, String> findPolicyNames(Collection<Long> policyIds) {
        if (policyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(POLICY_NAMES_SQL,
                Map.of("ids", policyIds),
                (rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("name")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static final String USER_NAMES_SQL = """
            SELECT u.id, u.display_name FROM app_user u WHERE u.id IN (:ids)
            """;

    public Map<Long, String> findUserNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(USER_NAMES_SQL,
                Map.of("ids", userIds),
                (rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("display_name")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public record OfferInfo(String name, String sellerSku, long connectionId) {}

    private void appendFilters(PricingRunFilter filter, StringBuilder where,
                               Map<String, Object> params) {
        if (filter == null) {
            return;
        }

        if (filter.connectionId() != null) {
            where.append(" AND r.connectionId = :connectionId");
            params.put("connectionId", filter.connectionId());
        }

        if (filter.status() != null && !filter.status().isEmpty()) {
            where.append(" AND r.status IN (:statuses)");
            params.put("statuses", filter.status());
        }

        if (filter.triggerType() != null && !filter.triggerType().isEmpty()) {
            where.append(" AND r.triggerType IN (:triggerTypes)");
            params.put("triggerTypes", filter.triggerType());
        }

        if (filter.from() != null) {
            where.append(" AND r.createdAt >= :from");
            params.put("from", filter.from());
        }

        if (filter.to() != null) {
            where.append(" AND r.createdAt <= :to");
            params.put("to", filter.to());
        }
    }
}
