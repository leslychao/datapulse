package io.datapulse.pricing.persistence;

import io.datapulse.pricing.api.PricingRunFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PricingRunReadRepository {

    private final EntityManager entityManager;

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

    private void appendFilters(PricingRunFilter filter, StringBuilder where,
                               Map<String, Object> params) {
        if (filter == null) {
            return;
        }

        if (filter.connectionId() != null) {
            where.append(" AND r.connectionId = :connectionId");
            params.put("connectionId", filter.connectionId());
        }

        if (filter.status() != null) {
            where.append(" AND r.status = :status");
            params.put("status", filter.status());
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
