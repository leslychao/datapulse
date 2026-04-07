package io.datapulse.etl.domain.source.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.OzonPromoReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonActionDto;
import io.datapulse.etl.adapter.ozon.dto.OzonActionProductDto;
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
import io.datapulse.etl.persistence.canonical.CanonicalPromoProductEntity;
import io.datapulse.etl.persistence.canonical.CanonicalPromoProductUpsertRepository;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferLookupRepository;
import io.datapulse.etl.persistence.canonical.PromoCampaignLookupRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OzonPromoSyncSource implements EventSource {

    private final OzonPromoReadAdapter adapter;
    private final OzonNormalizer normalizer;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;
    private final CanonicalPromoCampaignUpsertRepository campaignRepository;
    private final CanonicalPromoProductUpsertRepository productRepository;
    private final PromoCampaignLookupRepository campaignLookup;
    private final MarketplaceOfferLookupRepository offerLookup;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.PROMO_SYNC;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String clientId = ctx.credentials().get(CredentialKeys.OZON_CLIENT_ID);
        String apiKey = ctx.credentials().get(CredentialKeys.OZON_API_KEY);
        List<SubSourceResult> results = new ArrayList<>();

        SubSourceResult campaignResult = syncCampaigns(ctx, clientId, apiKey);
        results.add(campaignResult);

        if (!campaignResult.isSuccess()) {
            log.warn("Ozon actions sync failed, skipping products: connectionId={}",
                    ctx.connectionId());
            return results;
        }

        SubSourceResult productResult = syncProducts(ctx, clientId, apiKey);
        results.add(productResult);

        return results;
    }

    private SubSourceResult syncCampaigns(IngestContext ctx, String clientId, String apiKey) {
        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonActionList");
        List<CaptureResult> pages = adapter.captureActions(captureCtx, clientId, apiKey);

        return subSourceRunner.processPages(
                "OzonActionList", pages, OzonActionDto.class,
                batch -> campaignRepository.batchUpsert(batch.stream()
                        .map(dto -> mapper.toPromoCampaign(
                                normalizer.normalizeAction(dto), ctx))
                        .toList()));
    }

    private SubSourceResult syncProducts(IngestContext ctx, String clientId, String apiKey) {
        Map<String, Long> campaignMap = campaignLookup.findAllByConnection(ctx.connectionId());
        if (campaignMap.isEmpty()) {
            log.info("No promo campaigns found for Ozon connection, skipping products: connectionId={}",
                    ctx.connectionId());
            return SubSourceResult.success("OzonActionProducts", 0, 0);
        }

        int totalPages = 0;
        int totalRecords = 0;
        int totalSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (var entry : campaignMap.entrySet()) {
            String externalPromoId = entry.getKey();
            long campaignId = entry.getValue();
            long actionId = Long.parseLong(externalPromoId);

            try {
                SubSourceResult participating = syncActionProducts(
                        ctx, clientId, apiKey, actionId, campaignId, true);
                totalPages += participating.pagesProcessed();
                totalRecords += participating.recordsProcessed();
                totalSkipped += participating.recordsSkipped();
                errors.addAll(participating.errors());

                SubSourceResult candidates = syncActionProducts(
                        ctx, clientId, apiKey, actionId, campaignId, false);
                totalPages += candidates.pagesProcessed();
                totalRecords += candidates.recordsProcessed();
                totalSkipped += candidates.recordsSkipped();
                errors.addAll(candidates.errors());
            } catch (Exception e) {
                if (isActionNotFound(e)) {
                    log.warn("Ozon action no longer exists, skipping: connectionId={}, actionId={}",
                            ctx.connectionId(), externalPromoId);
                    continue;
                }
                log.error("Failed to sync products for Ozon action: connectionId={}, actionId={}, error={}",
                        ctx.connectionId(), externalPromoId, e.getMessage(), e);
                errors.add("Action %s: %s".formatted(externalPromoId, e.getMessage()));
            }
        }

        if (!errors.isEmpty() && totalRecords == 0) {
            return SubSourceResult.failed("OzonActionProducts", errors.get(0));
        }
        if (!errors.isEmpty()) {
            return SubSourceResult.partial("OzonActionProducts", null,
                    totalPages, totalRecords, totalSkipped, errors);
        }
        return SubSourceResult.success("OzonActionProducts", totalPages, totalRecords);
    }

    private SubSourceResult syncActionProducts(IngestContext ctx,
                                               String clientId, String apiKey,
                                               long actionId, long campaignId,
                                               boolean isParticipating) {
        String sourceLabel = isParticipating ? "products" : "candidates";
        var captureCtx = CaptureContextFactory.build(
                ctx, eventType(), "OzonAction-%s-%d".formatted(sourceLabel, actionId));

        List<CaptureResult> pages = isParticipating
                ? adapter.captureActionProducts(captureCtx, clientId, apiKey, actionId)
                : adapter.captureActionCandidates(captureCtx, clientId, apiKey, actionId);

        return subSourceRunner.processPages(
                "OzonAction-%s-%d".formatted(sourceLabel, actionId), pages,
                OzonActionProductDto.class,
                batch -> processProductBatch(batch, ctx, campaignId, actionId, isParticipating));
    }

    private static boolean isActionNotFound(Exception e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebClientResponseException.NotFound) {
                return true;
            }
        }
        return false;
    }

    private void processProductBatch(List<OzonActionProductDto> batch,
                                     IngestContext ctx,
                                     long campaignId,
                                     long actionId,
                                     boolean isParticipating) {
        List<CanonicalPromoProductEntity> entities = new ArrayList<>();

        for (OzonActionProductDto dto : batch) {
            String marketplaceSku = String.valueOf(dto.productId());
            var offerId = offerLookup.findByMarketplaceSku(ctx.connectionId(), marketplaceSku);
            if (offerId.isEmpty()) {
                log.warn("Offer lookup miss for Ozon promo product: connectionId={}, productId={}",
                        ctx.connectionId(), dto.productId());
                continue;
            }

            NormalizedPromoProduct norm = normalizer.normalizeActionProduct(
                    dto, actionId, isParticipating);
            entities.add(mapper.toPromoProduct(norm, campaignId, offerId.get(), ctx));
        }

        if (!entities.isEmpty()) {
            productRepository.batchUpsert(entities);
        }
    }
}
