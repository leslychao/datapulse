package io.datapulse.pricing.persistence;

import io.datapulse.pricing.api.PriceDecisionFilter;
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
public class PriceDecisionReadRepository {

    private final EntityManager entityManager;

    public Page<PriceDecisionEntity> findByFilter(long workspaceId, PriceDecisionFilter filter,
                                                  Pageable pageable) {
        var whereClause = new StringBuilder("WHERE d.workspaceId = :workspaceId");
        Map<String, Object> params = new HashMap<>();
        params.put("workspaceId", workspaceId);

        appendFilters(filter, whereClause, params);

        String countJpql = "SELECT COUNT(d) FROM PriceDecisionEntity d " + whereClause;
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        String dataJpql = "SELECT d FROM PriceDecisionEntity d " + whereClause + " ORDER BY d.createdAt DESC";
        TypedQuery<PriceDecisionEntity> dataQuery = entityManager.createQuery(dataJpql, PriceDecisionEntity.class);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(dataQuery.getResultList(), pageable, total);
    }

    private void appendFilters(PriceDecisionFilter filter, StringBuilder whereClause,
                               Map<String, Object> params) {
        if (filter == null) {
            return;
        }

        if (filter.connectionId() != null) {
            whereClause.append(" AND d.pricingRunId IN (SELECT r.id FROM PricingRunEntity r"
                    + " WHERE r.connectionId = :connectionId)");
            params.put("connectionId", filter.connectionId());
        }

        if (filter.pricingRunId() != null) {
            whereClause.append(" AND d.pricingRunId = :pricingRunId");
            params.put("pricingRunId", filter.pricingRunId());
        }

        if (filter.marketplaceOfferId() != null) {
            whereClause.append(" AND d.marketplaceOfferId = :marketplaceOfferId");
            params.put("marketplaceOfferId", filter.marketplaceOfferId());
        }

        if (filter.decisionType() != null) {
            whereClause.append(" AND d.decisionType = :decisionType");
            params.put("decisionType", filter.decisionType());
        }

        if (filter.from() != null) {
            whereClause.append(" AND d.createdAt >= :from");
            params.put("from", filter.from());
        }

        if (filter.to() != null) {
            whereClause.append(" AND d.createdAt <= :to");
            params.put("to", filter.to());
        }
    }
}
