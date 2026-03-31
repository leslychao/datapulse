package io.datapulse.etl.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryEntity;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.etl.persistence.canonical.WarehouseLookupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full canonical finance normalization pipeline:
 * NormalizedFinanceItem → resolve SKU → resolve warehouse → compute attribution → entity.
 *
 * <p>SKU resolution uses a per-batch cache to avoid redundant DB lookups
 * (the same SKU appears dozens of times in a typical finance batch).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanonicalFinanceNormalizer {

    private static final String ATTRIBUTION_POSTING = "POSTING";
    private static final String ATTRIBUTION_PRODUCT = "PRODUCT";
    private static final String ATTRIBUTION_ACCOUNT = "ACCOUNT";

    private final SkuLookupRepository skuLookup;
    private final WarehouseLookupRepository warehouseLookup;
    private final CanonicalEntityMapper mapper;

    /**
     * Normalizes a batch of NormalizedFinanceItems into canonical entities.
     * Uses a per-batch SKU cache for efficient lookups.
     */
    public List<CanonicalFinanceEntryEntity> normalizeBatch(List<NormalizedFinanceItem> items,
                                                            IngestContext ctx) {
        var skuCache = new HashMap<String, Optional<Long>>();
        var warehouseCache = new HashMap<String, Optional<Long>>();

        return items.stream()
                .map(item -> normalize(item, ctx, skuCache, warehouseCache))
                .toList();
    }

    /**
     * Single-item normalization (used when batch-level caching is not needed).
     */
    public CanonicalFinanceEntryEntity normalize(NormalizedFinanceItem raw, IngestContext ctx) {
        return normalize(raw, ctx, new HashMap<>(), new HashMap<>());
    }

    private CanonicalFinanceEntryEntity normalize(NormalizedFinanceItem raw,
                                                  IngestContext ctx,
                                                  Map<String, Optional<Long>> skuCache,
                                                  Map<String, Optional<Long>> warehouseCache) {
        Long sellerSkuId = resolveSellerSkuId(raw, ctx, skuCache);
        Long warehouseId = resolveWarehouseId(raw, ctx, warehouseCache);
        String attributionLevel = computeAttribution(raw, sellerSkuId);

        return mapper.toFinanceEntry(raw, ctx, sellerSkuId, warehouseId, attributionLevel);
    }

    /**
     * SKU resolution chain:
     * 1. marketplace_offer lookup by marketplaceSku + connectionId
     * 2. Fallback: product_master lookup by sellerSku (vendorCode) + workspaceId
     * 3. Miss: NULL + log.warn
     */
    private Long resolveSellerSkuId(NormalizedFinanceItem raw,
                                    IngestContext ctx,
                                    Map<String, Optional<Long>> cache) {
        String cacheKey = buildSkuCacheKey(raw);
        if (cacheKey == null) {
            return null;
        }

        Optional<Long> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.orElse(null);
        }

        Optional<Long> resolved = skuLookup.findByMarketplaceSku(
                ctx.connectionId(), raw.marketplaceSku());

        if (resolved.isEmpty() && raw.sellerSku() != null) {
            resolved = skuLookup.findByVendorCode(ctx.workspaceId(), raw.sellerSku());
        }

        if (resolved.isEmpty() && (raw.marketplaceSku() != null || raw.sellerSku() != null)) {
            log.warn("SKU lookup miss: marketplaceSku={}, sellerSku={}, connectionId={}",
                    raw.marketplaceSku(), raw.sellerSku(), ctx.connectionId());
        }

        cache.put(cacheKey, resolved);
        return resolved.orElse(null);
    }

    private Long resolveWarehouseId(NormalizedFinanceItem raw,
                                    IngestContext ctx,
                                    Map<String, Optional<Long>> cache) {
        String externalId = raw.warehouseExternalId();
        if (externalId == null) {
            return null;
        }

        Optional<Long> cached = cache.get(externalId);
        if (cached != null) {
            return cached.orElse(null);
        }

        Optional<Long> resolved = warehouseLookup.findByExternalId(ctx.connectionId(), externalId);
        cache.put(externalId, resolved);
        return resolved.orElse(null);
    }

    /**
     * Attribution level per etl-pipeline.md §attribution_level:
     * IF posting_id IS NOT NULL OR order_id IS NOT NULL → POSTING
     * ELIF seller_sku_id IS NOT NULL → PRODUCT
     * ELSE → ACCOUNT
     */
    private static String computeAttribution(NormalizedFinanceItem raw, Long sellerSkuId) {
        if (raw.postingId() != null || raw.orderId() != null) {
            return ATTRIBUTION_POSTING;
        }
        if (sellerSkuId != null) {
            return ATTRIBUTION_PRODUCT;
        }
        return ATTRIBUTION_ACCOUNT;
    }

    private static String buildSkuCacheKey(NormalizedFinanceItem raw) {
        if (raw.marketplaceSku() != null) {
            return "msku:" + raw.marketplaceSku();
        }
        if (raw.sellerSku() != null) {
            return "vc:" + raw.sellerSku();
        }
        return null;
    }
}
