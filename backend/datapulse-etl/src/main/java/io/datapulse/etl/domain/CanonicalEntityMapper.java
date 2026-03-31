package io.datapulse.etl.domain;

import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.domain.normalized.NormalizedCategory;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.domain.normalized.NormalizedOrderItem;
import io.datapulse.etl.domain.normalized.NormalizedPriceItem;
import io.datapulse.etl.domain.normalized.NormalizedReturnItem;
import io.datapulse.etl.domain.normalized.NormalizedSaleItem;
import io.datapulse.etl.domain.normalized.NormalizedStockItem;
import io.datapulse.etl.domain.normalized.NormalizedWarehouse;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryEntity;
import io.datapulse.etl.persistence.canonical.CanonicalOrderEntity;
import io.datapulse.etl.persistence.canonical.CanonicalPriceCurrentEntity;
import io.datapulse.etl.persistence.canonical.CanonicalReturnEntity;
import io.datapulse.etl.persistence.canonical.CanonicalSaleEntity;
import io.datapulse.etl.persistence.canonical.CanonicalStockCurrentEntity;
import io.datapulse.etl.persistence.canonical.CategoryEntity;
import io.datapulse.etl.persistence.canonical.ProductMasterEntity;
import io.datapulse.etl.persistence.canonical.WarehouseEntity;
import org.springframework.stereotype.Service;

/**
 * Stateless mapper: normalized domain records → canonical persistence entities.
 * Sets context fields (connectionId, marketplace, jobExecutionId) from {@link IngestContext}.
 *
 * <p>Shared by all {@link EventSource} implementations to avoid duplicating
 * entity construction logic.</p>
 */
@Service
public class CanonicalEntityMapper {

    public WarehouseEntity toWarehouse(NormalizedWarehouse norm, IngestContext ctx) {
        var entity = new WarehouseEntity();
        entity.setMarketplaceConnectionId(ctx.connectionId());
        entity.setExternalWarehouseId(norm.externalWarehouseId());
        entity.setName(norm.name());
        entity.setWarehouseType(norm.warehouseType());
        entity.setMarketplaceType(platformName(ctx));
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public CategoryEntity toCategory(NormalizedCategory norm, IngestContext ctx) {
        var entity = new CategoryEntity();
        entity.setMarketplaceConnectionId(ctx.connectionId());
        entity.setExternalCategoryId(norm.externalCategoryId());
        entity.setName(norm.name());
        entity.setMarketplaceType(platformName(ctx));
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public ProductMasterEntity toProductMaster(NormalizedCatalogItem norm, IngestContext ctx) {
        var entity = new ProductMasterEntity();
        entity.setWorkspaceId(ctx.workspaceId());
        entity.setExternalCode(norm.sellerSku());
        entity.setName(norm.name());
        entity.setBrand(norm.brand());
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public CanonicalPriceCurrentEntity toPrice(NormalizedPriceItem norm, IngestContext ctx) {
        var entity = new CanonicalPriceCurrentEntity();
        entity.setPrice(norm.price());
        entity.setDiscountPrice(norm.discountPrice());
        entity.setDiscountPct(norm.discountPct());
        entity.setCurrency(norm.currency());
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public CanonicalStockCurrentEntity toStock(NormalizedStockItem norm, IngestContext ctx) {
        var entity = new CanonicalStockCurrentEntity();
        entity.setAvailable(norm.available());
        entity.setReserved(norm.reserved());
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public CanonicalOrderEntity toOrder(NormalizedOrderItem norm, IngestContext ctx) {
        var entity = new CanonicalOrderEntity();
        entity.setConnectionId(ctx.connectionId());
        entity.setSourcePlatform(platformName(ctx));
        entity.setExternalOrderId(norm.externalOrderId());
        entity.setOrderDate(norm.orderDate());
        entity.setQuantity(norm.quantity());
        entity.setPricePerUnit(norm.pricePerUnit());
        entity.setCurrency(norm.currency());
        entity.setStatus(norm.status());
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public CanonicalSaleEntity toSale(NormalizedSaleItem norm, IngestContext ctx) {
        var entity = new CanonicalSaleEntity();
        entity.setConnectionId(ctx.connectionId());
        entity.setSourcePlatform(platformName(ctx));
        entity.setExternalSaleId(norm.externalSaleId());
        entity.setSaleDate(norm.saleDate());
        entity.setSaleAmount(norm.saleAmount());
        entity.setCommission(norm.commission());
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    public CanonicalReturnEntity toReturn(NormalizedReturnItem norm, IngestContext ctx) {
        var entity = new CanonicalReturnEntity();
        entity.setConnectionId(ctx.connectionId());
        entity.setSourcePlatform(platformName(ctx));
        entity.setExternalReturnId(norm.externalReturnId());
        entity.setReturnDate(norm.returnDate());
        entity.setReturnAmount(norm.returnAmount());
        entity.setReturnReason(norm.returnReason());
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    /**
     * Maps a NormalizedFinanceItem to a canonical entity with all 12 measure columns,
     * resolved seller_sku_id, warehouse_id, and computed attribution_level.
     *
     * @param sellerSkuId      resolved FK to seller_sku (nullable — SKU lookup miss)
     * @param warehouseId      resolved FK to warehouse (nullable — non-warehouse ops)
     * @param attributionLevel computed: POSTING, PRODUCT, or ACCOUNT
     */
    public CanonicalFinanceEntryEntity toFinanceEntry(NormalizedFinanceItem norm,
                                                      IngestContext ctx,
                                                      Long sellerSkuId,
                                                      Long warehouseId,
                                                      String attributionLevel) {
        var entity = new CanonicalFinanceEntryEntity();
        entity.setConnectionId(ctx.connectionId());
        entity.setSourcePlatform(platformName(ctx));
        entity.setExternalEntryId(norm.externalEntryId());
        entity.setEntryType(norm.entryType().canonicalName());
        entity.setPostingId(norm.postingId());
        entity.setOrderId(norm.orderId());
        entity.setSellerSkuId(sellerSkuId);
        entity.setWarehouseId(warehouseId);
        entity.setRevenueAmount(norm.revenueAmount());
        entity.setMarketplaceCommissionAmount(norm.marketplaceCommissionAmount());
        entity.setAcquiringCommissionAmount(norm.acquiringCommissionAmount());
        entity.setLogisticsCostAmount(norm.logisticsCostAmount());
        entity.setStorageCostAmount(norm.storageCostAmount());
        entity.setPenaltiesAmount(norm.penaltiesAmount());
        entity.setAcceptanceCostAmount(norm.acceptanceCostAmount());
        entity.setMarketingCostAmount(norm.marketingCostAmount());
        entity.setOtherMarketplaceChargesAmount(norm.otherMarketplaceChargesAmount());
        entity.setCompensationAmount(norm.compensationAmount());
        entity.setRefundAmount(norm.refundAmount());
        entity.setNetPayout(norm.netPayout());
        entity.setCurrency(norm.currency());
        entity.setEntryDate(norm.entryDate());
        entity.setAttributionLevel(attributionLevel);
        entity.setJobExecutionId(ctx.jobExecutionId());
        return entity;
    }

    private String platformName(IngestContext ctx) {
        return ctx.marketplace().name().toLowerCase();
    }
}
