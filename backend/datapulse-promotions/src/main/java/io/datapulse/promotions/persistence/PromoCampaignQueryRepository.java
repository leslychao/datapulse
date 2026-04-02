package io.datapulse.promotions.persistence;

import io.datapulse.promotions.api.PromoCampaignDetailResponse;
import io.datapulse.promotions.api.PromoCampaignProductResponse;
import io.datapulse.promotions.api.PromoCampaignSummaryResponse;
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
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PromoCampaignQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String BASE_SELECT = """
            SELECT cpc.id, cpc.promo_name, cpc.source_platform,
                   cpc.promo_type, cpc.mechanic, cpc.status,
                   cpc.date_from, cpc.date_to, cpc.freeze_at,
                   cpc.connection_id, mc.name AS connection_name,
                   (SELECT count(*) FROM canonical_promo_product cpp
                    WHERE cpp.canonical_promo_campaign_id = cpc.id
                      AND cpp.participation_status = 'ELIGIBLE') AS eligible_count,
                   (SELECT count(*) FROM canonical_promo_product cpp
                    WHERE cpp.canonical_promo_campaign_id = cpc.id
                      AND cpp.participation_status = 'PARTICIPATING') AS participated_count
            FROM canonical_promo_campaign cpc
            JOIN marketplace_connection mc ON cpc.connection_id = mc.id
            WHERE mc.workspace_id = :workspaceId
            """;

    public Page<PromoCampaignSummaryResponse> listCampaigns(long workspaceId, Long connectionId,
                                                             Collection<String> statuses,
                                                             Collection<String> marketplaceTypes,
                                                             LocalDate from, LocalDate to,
                                                             Pageable pageable) {
        var where = new StringBuilder(BASE_SELECT);
        var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);

        if (connectionId != null) {
            where.append(" AND cpc.connection_id = :connectionId");
            params.addValue("connectionId", connectionId);
        }
        if (statuses != null && !statuses.isEmpty()) {
            where.append(" AND cpc.status IN (:statuses)");
            params.addValue("statuses", statuses);
        }
        if (marketplaceTypes != null && !marketplaceTypes.isEmpty()) {
            where.append(" AND cpc.source_platform IN (:marketplaceTypes)");
            params.addValue("marketplaceTypes", marketplaceTypes);
        }
        if (from != null) {
            where.append(" AND cpc.date_to >= :from");
            params.addValue("from", from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND cpc.date_from < :toExclusive");
            params.addValue("toExclusive", to.plusDays(1).atStartOfDay());
        }

        var countSql = "SELECT count(*) FROM (" + where + ") sub";
        int total = Optional.ofNullable(jdbcTemplate.queryForObject(countSql, params, Integer.class))
                .orElse(0);

        where.append(" ORDER BY cpc.date_from DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PromoCampaignSummaryResponse> content = jdbcTemplate.query(
                where.toString(), params, (rs, rowNum) -> new PromoCampaignSummaryResponse(
                        rs.getLong("id"),
                        rs.getString("promo_name"),
                        rs.getString("source_platform"),
                        rs.getString("promo_type"),
                        rs.getString("mechanic"),
                        rs.getObject("date_from", OffsetDateTime.class),
                        rs.getObject("date_to", OffsetDateTime.class),
                        rs.getObject("freeze_at", OffsetDateTime.class),
                        rs.getInt("eligible_count"),
                        rs.getInt("participated_count"),
                        rs.getString("status"),
                        rs.getLong("connection_id"),
                        rs.getString("connection_name")
                ));

        return new PageImpl<>(content, pageable, total);
    }

    public Optional<PromoCampaignDetailResponse> getCampaign(long campaignId, long workspaceId) {
        var params = new MapSqlParameterSource()
                .addValue("campaignId", campaignId)
                .addValue("workspaceId", workspaceId);

        return jdbcTemplate.query("""
                SELECT cpc.*,
                       (SELECT count(*) FROM canonical_promo_product cpp
                        WHERE cpp.canonical_promo_campaign_id = cpc.id) AS total_products,
                       (SELECT count(*) FROM canonical_promo_product cpp
                        WHERE cpp.canonical_promo_campaign_id = cpc.id
                          AND cpp.participation_status = 'ELIGIBLE') AS eligible_count,
                       (SELECT count(*) FROM canonical_promo_product cpp
                        WHERE cpp.canonical_promo_campaign_id = cpc.id
                          AND cpp.participation_status = 'PARTICIPATING') AS participating_count,
                       (SELECT count(*) FROM canonical_promo_product cpp
                        WHERE cpp.canonical_promo_campaign_id = cpc.id
                          AND cpp.participation_status IN ('DECLINED', 'AUTO_DECLINED')) AS declined_count
                FROM canonical_promo_campaign cpc
                JOIN marketplace_connection mc ON cpc.connection_id = mc.id
                WHERE cpc.id = :campaignId AND mc.workspace_id = :workspaceId
                """, params, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new PromoCampaignDetailResponse(
                    rs.getLong("id"),
                    rs.getLong("connection_id"),
                    rs.getString("external_promo_id"),
                    rs.getString("source_platform"),
                    rs.getString("promo_name"),
                    rs.getString("promo_type"),
                    rs.getString("status"),
                    rs.getObject("date_from", OffsetDateTime.class),
                    rs.getObject("date_to", OffsetDateTime.class),
                    rs.getObject("freeze_at", OffsetDateTime.class),
                    rs.getString("description"),
                    rs.getString("mechanic"),
                    rs.getObject("is_participating", Boolean.class),
                    rs.getObject("synced_at", OffsetDateTime.class),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getInt("total_products"),
                    rs.getInt("eligible_count"),
                    rs.getInt("participating_count"),
                    rs.getInt("declined_count")
            ));
        });
    }

    public Page<PromoCampaignProductResponse> getCampaignProducts(long campaignId,
                                                                    String participationStatus,
                                                                    String search,
                                                                    Pageable pageable) {
        var where = new StringBuilder("""
                SELECT cpp.id, cpp.marketplace_offer_id, cpp.participation_status,
                       cpp.required_price, cpp.current_price, cpp.max_promo_price,
                       cpp.max_discount_pct, cpp.stock_available, cpp.add_mode,
                       cpp.participation_decision_source,
                       mo.name AS offer_name, mo.marketplace_sku
                FROM canonical_promo_product cpp
                JOIN marketplace_offer mo ON cpp.marketplace_offer_id = mo.id
                WHERE cpp.canonical_promo_campaign_id = :campaignId
                """);

        var params = new MapSqlParameterSource().addValue("campaignId", campaignId);

        if (participationStatus != null) {
            where.append(" AND cpp.participation_status = :participationStatus");
            params.addValue("participationStatus", participationStatus);
        }
        if (search != null) {
            where.append(" AND (mo.name ILIKE :search OR mo.marketplace_sku ILIKE :search)");
            params.addValue("search", "%" + search + "%");
        }

        var countSql = "SELECT count(*) FROM (" + where + ") sub";
        int total = Optional.ofNullable(jdbcTemplate.queryForObject(countSql, params, Integer.class))
                .orElse(0);

        where.append(" ORDER BY mo.name LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PromoCampaignProductResponse> content = jdbcTemplate.query(
                where.toString(), params, (rs, rowNum) -> new PromoCampaignProductResponse(
                        rs.getLong("id"),
                        rs.getLong("marketplace_offer_id"),
                        rs.getString("participation_status"),
                        rs.getBigDecimal("required_price"),
                        rs.getBigDecimal("current_price"),
                        rs.getBigDecimal("max_promo_price"),
                        rs.getBigDecimal("max_discount_pct"),
                        rs.getObject("stock_available", Integer.class),
                        rs.getString("add_mode"),
                        rs.getString("participation_decision_source"),
                        rs.getString("offer_name"),
                        rs.getString("marketplace_sku")
                ));

        return new PageImpl<>(content, pageable, total);
    }
}
