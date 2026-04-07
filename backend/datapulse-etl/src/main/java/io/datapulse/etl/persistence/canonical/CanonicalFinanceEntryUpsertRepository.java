package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalFinanceEntryUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_finance_entry (workspace_id, connection_id, source_platform,
                                                 external_entry_id, entry_type, posting_id,
                                                 order_id, seller_sku_id, warehouse_id,
                                                 revenue_amount, marketplace_commission_amount,
                                                 acquiring_commission_amount, logistics_cost_amount,
                                                 storage_cost_amount, penalties_amount,
                                                 acceptance_cost_amount, marketing_cost_amount,
                                                 other_marketplace_charges_amount, compensation_amount,
                                                 refund_amount, net_payout,
                                                 currency, entry_date, attribution_level,
                                                 fulfillment_type,
                                                 job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, source_platform, external_entry_id) DO UPDATE SET
                connection_id = EXCLUDED.connection_id,
                entry_type = EXCLUDED.entry_type,
                posting_id = EXCLUDED.posting_id,
                order_id = EXCLUDED.order_id,
                seller_sku_id = EXCLUDED.seller_sku_id,
                warehouse_id = EXCLUDED.warehouse_id,
                revenue_amount = EXCLUDED.revenue_amount,
                marketplace_commission_amount = EXCLUDED.marketplace_commission_amount,
                acquiring_commission_amount = EXCLUDED.acquiring_commission_amount,
                logistics_cost_amount = EXCLUDED.logistics_cost_amount,
                storage_cost_amount = EXCLUDED.storage_cost_amount,
                penalties_amount = EXCLUDED.penalties_amount,
                acceptance_cost_amount = EXCLUDED.acceptance_cost_amount,
                marketing_cost_amount = EXCLUDED.marketing_cost_amount,
                other_marketplace_charges_amount = EXCLUDED.other_marketplace_charges_amount,
                compensation_amount = EXCLUDED.compensation_amount,
                refund_amount = EXCLUDED.refund_amount,
                net_payout = EXCLUDED.net_payout,
                currency = EXCLUDED.currency,
                entry_date = EXCLUDED.entry_date,
                attribution_level = EXCLUDED.attribution_level,
                fulfillment_type = EXCLUDED.fulfillment_type,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (canonical_finance_entry.entry_type, canonical_finance_entry.posting_id,
                   canonical_finance_entry.order_id, canonical_finance_entry.seller_sku_id,
                   canonical_finance_entry.warehouse_id,
                   canonical_finance_entry.revenue_amount, canonical_finance_entry.marketplace_commission_amount,
                   canonical_finance_entry.acquiring_commission_amount, canonical_finance_entry.logistics_cost_amount,
                   canonical_finance_entry.storage_cost_amount, canonical_finance_entry.penalties_amount,
                   canonical_finance_entry.acceptance_cost_amount, canonical_finance_entry.marketing_cost_amount,
                   canonical_finance_entry.other_marketplace_charges_amount, canonical_finance_entry.compensation_amount,
                   canonical_finance_entry.refund_amount, canonical_finance_entry.net_payout,
                   canonical_finance_entry.currency, canonical_finance_entry.entry_date,
                   canonical_finance_entry.attribution_level,
                   canonical_finance_entry.fulfillment_type)
                IS DISTINCT FROM
                  (EXCLUDED.entry_type, EXCLUDED.posting_id,
                   EXCLUDED.order_id, EXCLUDED.seller_sku_id,
                   EXCLUDED.warehouse_id,
                   EXCLUDED.revenue_amount, EXCLUDED.marketplace_commission_amount,
                   EXCLUDED.acquiring_commission_amount, EXCLUDED.logistics_cost_amount,
                   EXCLUDED.storage_cost_amount, EXCLUDED.penalties_amount,
                   EXCLUDED.acceptance_cost_amount, EXCLUDED.marketing_cost_amount,
                   EXCLUDED.other_marketplace_charges_amount, EXCLUDED.compensation_amount,
                   EXCLUDED.refund_amount, EXCLUDED.net_payout,
                   EXCLUDED.currency, EXCLUDED.entry_date,
                   EXCLUDED.attribution_level,
                   EXCLUDED.fulfillment_type)
            """;

    public void batchUpsert(List<CanonicalFinanceEntryEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getConnectionId());
                    ps.setString(3, e.getSourcePlatform());
                    ps.setString(4, e.getExternalEntryId());
                    ps.setString(5, e.getEntryType());
                    ps.setString(6, e.getPostingId());
                    ps.setString(7, e.getOrderId());
                    ps.setObject(8, e.getSellerSkuId());
                    ps.setObject(9, e.getWarehouseId());
                    ps.setBigDecimal(10, e.getRevenueAmount());
                    ps.setBigDecimal(11, e.getMarketplaceCommissionAmount());
                    ps.setBigDecimal(12, e.getAcquiringCommissionAmount());
                    ps.setBigDecimal(13, e.getLogisticsCostAmount());
                    ps.setBigDecimal(14, e.getStorageCostAmount());
                    ps.setBigDecimal(15, e.getPenaltiesAmount());
                    ps.setBigDecimal(16, e.getAcceptanceCostAmount());
                    ps.setBigDecimal(17, e.getMarketingCostAmount());
                    ps.setBigDecimal(18, e.getOtherMarketplaceChargesAmount());
                    ps.setBigDecimal(19, e.getCompensationAmount());
                    ps.setBigDecimal(20, e.getRefundAmount());
                    ps.setBigDecimal(21, e.getNetPayout());
                    ps.setString(22, e.getCurrency());
                    ps.setTimestamp(23, Timestamp.from(e.getEntryDate().toInstant()));
                    ps.setString(24, e.getAttributionLevel());
                    ps.setString(25, e.getFulfillmentType());
                    ps.setLong(26, e.getJobExecutionId());
                });
    }
}
