package io.datapulse.promotions.domain;

import io.datapulse.promotions.persistence.PromoPolicyAssignmentEntity;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import io.datapulse.promotions.persistence.PromoProductRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoPolicyResolver {

    private final PromoPolicyRepository policyRepository;
    private final PromoPolicyAssignmentRepository assignmentRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String LOAD_ELIGIBLE_PRODUCTS = """
            SELECT cpp.id AS promo_product_id,
                   cpp.canonical_promo_campaign_id AS campaign_id,
                   cpp.marketplace_offer_id,
                   mo.category_id,
                   cpp.participation_status,
                   cpp.required_price,
                   cpp.current_price,
                   cp.cost_price AS cogs,
                   cpp.stock_available,
                   cpc.date_from,
                   cpc.date_to,
                   cpc.freeze_at
            FROM canonical_promo_product cpp
            JOIN canonical_promo_campaign cpc ON cpp.canonical_promo_campaign_id = cpc.id
            JOIN marketplace_offer mo ON cpp.marketplace_offer_id = mo.id
            LEFT JOIN seller_sku ss ON mo.seller_sku_id = ss.id
            LEFT JOIN cost_profile cp ON ss.id = cp.seller_sku_id
                AND cp.valid_from <= CURRENT_DATE
                AND (cp.valid_to IS NULL OR cp.valid_to >= CURRENT_DATE)
            WHERE cpc.connection_id = :connectionId
              AND cpc.status IN ('UPCOMING', 'ACTIVE')
              AND (cpc.freeze_at IS NULL OR cpc.freeze_at > NOW())
              AND cpp.participation_status IN ('ELIGIBLE', 'PARTICIPATING')
            """;

    public List<PromoProductRow> loadEligibleProducts(long connectionId, long workspaceId) {
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId);

        return jdbcTemplate.query(LOAD_ELIGIBLE_PRODUCTS, params, (rs, rowNum) ->
                new PromoProductRow(
                        rs.getLong("promo_product_id"),
                        rs.getLong("campaign_id"),
                        rs.getLong("marketplace_offer_id"),
                        rs.getObject("category_id", Long.class),
                        rs.getString("participation_status"),
                        rs.getBigDecimal("required_price"),
                        rs.getBigDecimal("current_price"),
                        rs.getBigDecimal("cogs"),
                        null,
                        rs.getObject("stock_available", Integer.class),
                        null,
                        rs.getObject("date_from", java.time.OffsetDateTime.class),
                        rs.getObject("date_to", java.time.OffsetDateTime.class),
                        rs.getObject("freeze_at", java.time.OffsetDateTime.class)
                ));
    }

    public PromoPolicyEntity resolvePolicy(long marketplaceOfferId, Long categoryId,
                                            long connectionId, long workspaceId) {
        List<PromoPolicyAssignmentEntity> assignments = assignmentRepository
                .findAllByMarketplaceConnectionId(connectionId);

        if (assignments.isEmpty()) {
            return null;
        }

        Map<Long, PromoPolicyEntity> policiesById = policyRepository
                .findAllByWorkspaceIdAndStatus(workspaceId, PromoPolicyStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(PromoPolicyEntity::getId, p -> p));

        PromoPolicyAssignmentEntity bestMatch = null;
        int bestSpecificity = -1;

        for (PromoPolicyAssignmentEntity assignment : assignments) {
            PromoPolicyEntity policy = policiesById.get(assignment.getPromoPolicyId());
            if (policy == null) {
                continue;
            }

            int specificity = switch (assignment.getScopeType()) {
                case SKU -> {
                    if (assignment.getMarketplaceOfferId() != null
                            && assignment.getMarketplaceOfferId().equals(marketplaceOfferId)) {
                        yield 3;
                    }
                    yield -1;
                }
                case CATEGORY -> {
                    if (assignment.getCategoryId() != null
                            && assignment.getCategoryId().equals(categoryId)) {
                        yield 2;
                    }
                    yield -1;
                }
                case CONNECTION -> 1;
            };

            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                bestMatch = assignment;
            }
        }

        if (bestMatch == null) {
            return null;
        }

        return policiesById.get(bestMatch.getPromoPolicyId());
    }
}
