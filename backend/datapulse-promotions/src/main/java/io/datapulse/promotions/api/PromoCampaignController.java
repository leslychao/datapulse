package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/promo/campaigns", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoCampaignController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<Map<String, Object>> listCampaigns(
            @RequestParam(value = "connectionId", required = false) Long connectionId,
            @RequestParam(value = "status", required = false) String status) {

        var sql = new StringBuilder("""
                SELECT cpc.id, cpc.connection_id, cpc.external_promo_id, cpc.source_platform,
                       cpc.promo_name, cpc.promo_type, cpc.status, cpc.date_from, cpc.date_to,
                       cpc.freeze_at, cpc.description, cpc.mechanic, cpc.is_participating,
                       cpc.synced_at, cpc.created_at, cpc.updated_at
                FROM canonical_promo_campaign cpc
                JOIN marketplace_connection mc ON cpc.connection_id = mc.id
                WHERE mc.workspace_id = :workspaceId
                """);

        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceContext.getWorkspaceId());

        if (connectionId != null) {
            sql.append(" AND cpc.connection_id = :connectionId");
            params.addValue("connectionId", connectionId);
        }
        if (status != null) {
            sql.append(" AND cpc.status = :status");
            params.addValue("status", status);
        }

        sql.append(" ORDER BY cpc.date_from DESC");

        return jdbcTemplate.queryForList(sql.toString(), params);
    }

    @GetMapping("/{campaignId}")
    public Map<String, Object> getCampaign(@PathVariable("campaignId") Long campaignId) {
        var params = new MapSqlParameterSource()
                .addValue("campaignId", campaignId)
                .addValue("workspaceId", workspaceContext.getWorkspaceId());

        return jdbcTemplate.queryForMap("""
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
                """, params);
    }

    @GetMapping("/{campaignId}/products")
    public List<Map<String, Object>> getCampaignProducts(
            @PathVariable("campaignId") Long campaignId,
            @RequestParam(value = "participationStatus", required = false) String participationStatus) {

        var sql = new StringBuilder("""
                SELECT cpp.id, cpp.marketplace_offer_id, cpp.participation_status,
                       cpp.required_price, cpp.current_price, cpp.max_promo_price,
                       cpp.max_discount_pct, cpp.stock_available, cpp.add_mode,
                       cpp.participation_decision_source,
                       mo.name AS offer_name, mo.marketplace_sku
                FROM canonical_promo_product cpp
                JOIN marketplace_offer mo ON cpp.marketplace_offer_id = mo.id
                WHERE cpp.canonical_promo_campaign_id = :campaignId
                """);

        var params = new MapSqlParameterSource()
                .addValue("campaignId", campaignId);

        if (participationStatus != null) {
            sql.append(" AND cpp.participation_status = :participationStatus");
            params.addValue("participationStatus", participationStatus);
        }

        sql.append(" ORDER BY mo.name");

        return jdbcTemplate.queryForList(sql.toString(), params);
    }
}
