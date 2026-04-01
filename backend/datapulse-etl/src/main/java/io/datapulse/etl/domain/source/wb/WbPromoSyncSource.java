package io.datapulse.etl.domain.source.wb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.WbPromoReadAdapter;
import io.datapulse.etl.adapter.wb.dto.WbPromotionDto;
import io.datapulse.etl.adapter.wb.dto.WbPromotionNomenclatureDto;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.domain.normalized.NormalizedPromoProduct;
import io.datapulse.etl.persistence.canonical.CanonicalPromoCampaignUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalPromoProductUpsertRepository;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferLookupRepository;
import io.datapulse.etl.persistence.canonical.PromoCampaignLookupRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WbPromoSyncSource implements EventSource {

    private final WbPromoReadAdapter adapter;
    private final WbNormalizer normalizer;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;
    private final CanonicalPromoCampaignUpsertRepository campaignRepository;
    private final CanonicalPromoProductUpsertRepository productRepository;
    private final PromoCampaignLookupRepository campaignLookup;
    private final MarketplaceOfferLookupRepository offerLookup;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.PROMO_SYNC;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String token = ctx.credentials().get("apiToken");
        List<SubSourceResult> results = new ArrayList<>();

        SubSourceResult campaignResult = syncCampaigns(ctx, token);
        results.add(campaignResult);

        if (!campaignResult.isSuccess()) {
            log.warn("WB promo campaigns sync failed, skipping nomenclatures: connectionId={}",
                    ctx.connectionId());
            return results;
        }

        SubSourceResult productResult = syncProducts(ctx, token);
        results.add(productResult);

        return results;
    }

    private SubSourceResult syncCampaigns(IngestContext ctx, String token) {
        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbPromoList");
        List<CaptureResult> pages = adapter.capturePromotions(captureCtx, token);

        return subSourceRunner.processPages(
                "WbPromoList", pages, WbPromotionDto.class,
                batch -> campaignRepository.batchUpsert(batch.stream()
                        .map(dto -> mapper.toPromoCampaign(
                                normalizer.normalizePromoCampaign(dto), ctx))
                        .toList()));
    }

    private SubSourceResult syncProducts(IngestContext ctx, String token) {
        Map<String, Long> campaignMap = campaignLookup.findAllByConnection(ctx.connectionId());
        if (campaignMap.isEmpty()) {
            log.info("No promo campaigns found for WB connection, skipping products: connectionId={}",
                    ctx.connectionId());
            return SubSourceResult.success("WbPromoNomenclatures", 0, 0);
        }

        int totalPages = 0;
        int totalRecords = 0;
        int totalSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (var entry : campaignMap.entrySet()) {
            String externalPromoId = entry.getKey();
            long campaignId = entry.getValue();
            long promotionId = Long.parseLong(externalPromoId);

            try {
                var captureCtx = CaptureContextFactory.build(
                        ctx, eventType(), "WbPromoNom-" + externalPromoId);

                List<CaptureResult> participatingPages =
                        adapter.captureNomenclatures(captureCtx, token, promotionId, true);
                List<CaptureResult> eligiblePages =
                        adapter.captureNomenclatures(captureCtx, token, promotionId, false);

                List<CaptureResult> allPages = new ArrayList<>(participatingPages);
                allPages.addAll(eligiblePages);

                SubSourceResult result = subSourceRunner.processPages(
                        "WbPromoNom-" + externalPromoId, allPages,
                        WbPromotionNomenclatureDto.class,
                        batch -> processNomenclatureBatch(batch, ctx, campaignId, promotionId));

                totalPages += result.pagesProcessed();
                totalRecords += result.recordsProcessed();
                totalSkipped += result.recordsSkipped();
                if (!result.errors().isEmpty()) {
                    errors.addAll(result.errors());
                }
            } catch (Exception e) {
                log.error("Failed to sync nomenclatures for WB promo: connectionId={}, promoId={}, error={}",
                        ctx.connectionId(), externalPromoId, e.getMessage(), e);
                errors.add("Promo %s: %s".formatted(externalPromoId, e.getMessage()));
            }
        }

        if (!errors.isEmpty() && totalRecords == 0) {
            return SubSourceResult.failed("WbPromoNomenclatures", errors.get(0));
        }
        if (!errors.isEmpty()) {
            return SubSourceResult.partial("WbPromoNomenclatures", null,
                    totalPages, totalRecords, totalSkipped, errors);
        }
        return SubSourceResult.success("WbPromoNomenclatures", totalPages, totalRecords);
    }

    private void processNomenclatureBatch(List<WbPromotionNomenclatureDto> batch,
                                          IngestContext ctx,
                                          long campaignId,
                                          long promotionId) {
        List<io.datapulse.etl.persistence.canonical.CanonicalPromoProductEntity> entities =
                new ArrayList<>();

        for (WbPromotionNomenclatureDto dto : batch) {
            if (dto.nmId() == null) {
                log.warn("WB promo nomenclature without nmId, skipping: promoId={}", promotionId);
                continue;
            }
            String marketplaceSku = String.valueOf(dto.nmId());
            var offerId = offerLookup.findByMarketplaceSku(ctx.connectionId(), marketplaceSku);
            if (offerId.isEmpty()) {
                log.warn("Offer lookup miss for WB promo product: connectionId={}, nmId={}",
                        ctx.connectionId(), dto.nmId());
                continue;
            }

            NormalizedPromoProduct norm = normalizer.normalizePromoNomenclature(dto, promotionId);
            entities.add(mapper.toPromoProduct(norm, campaignId, offerId.get(), ctx));
        }

        if (!entities.isEmpty()) {
            productRepository.batchUpsert(entities);
        }
    }
}
