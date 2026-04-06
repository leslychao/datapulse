package io.datapulse.pricing.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.persistence.CompetitorMatchEntity;
import io.datapulse.pricing.persistence.CompetitorMatchRepository;
import io.datapulse.pricing.persistence.CompetitorObservationEntity;
import io.datapulse.pricing.persistence.CompetitorObservationRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private final CompetitorMatchRepository matchRepository;
    private final CompetitorObservationRepository observationRepository;
    private final PricingDataReadRepository dataReadRepository;

    @Transactional(readOnly = true)
    public List<CompetitorMatchEntity> listMatches(long workspaceId,
                                                   Long marketplaceOfferId) {
        if (marketplaceOfferId != null) {
            return matchRepository.findAllByWorkspaceIdAndMarketplaceOfferId(
                    workspaceId, marketplaceOfferId);
        }
        return matchRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional
    public CompetitorMatchEntity createMatch(long workspaceId, long marketplaceOfferId,
                                             String competitorName,
                                             String competitorListingUrl,
                                             Long userId) {
        var entity = new CompetitorMatchEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setMarketplaceOfferId(marketplaceOfferId);
        entity.setCompetitorName(competitorName);
        entity.setCompetitorListingUrl(competitorListingUrl);
        entity.setMatchMethod("MANUAL");
        entity.setTrustLevel("TRUSTED");
        entity.setMatchedBy(userId);

        CompetitorMatchEntity saved = matchRepository.save(entity);
        log.info("Competitor match created: id={}, workspace={}, offer={}",
                saved.getId(), workspaceId, marketplaceOfferId);
        return saved;
    }

    @Transactional
    public void deleteMatch(long matchId, long workspaceId) {
        CompetitorMatchEntity match = matchRepository.findByIdAndWorkspaceId(matchId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("CompetitorMatch", matchId));
        observationRepository.deleteAllByCompetitorMatchId(matchId);
        matchRepository.delete(match);
        log.info("Competitor match deleted: id={}, workspace={}", matchId, workspaceId);
    }

    @Transactional
    public CompetitorObservationEntity addObservation(long matchId, long workspaceId,
                                                     BigDecimal competitorPrice,
                                                     OffsetDateTime observedAt) {
        CompetitorMatchEntity match = matchRepository.findByIdAndWorkspaceId(matchId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("CompetitorMatch", matchId));

        var entity = new CompetitorObservationEntity();
        entity.setCompetitorMatchId(match.getId());
        entity.setCompetitorPrice(competitorPrice);
        entity.setObservedAt(observedAt != null ? observedAt : OffsetDateTime.now());

        CompetitorObservationEntity saved = observationRepository.save(entity);
        log.info("Competitor observation added: matchId={}, price={}", matchId, competitorPrice);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CompetitorObservationEntity> listObservations(long matchId, long workspaceId) {
        matchRepository.findByIdAndWorkspaceId(matchId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("CompetitorMatch", matchId));
        return observationRepository.findAllByCompetitorMatchIdOrderByObservedAtDesc(matchId);
    }

    @Transactional
    public BulkUploadResult bulkUploadCsv(long workspaceId, long userId,
                                          InputStream csvStream) {
        List<CsvRow> rows = parseCsv(csvStream);
        if (rows.isEmpty()) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "CSV file is empty or has no valid rows");
        }

        List<String> skuCodes = rows.stream()
                .map(CsvRow::skuCode)
                .distinct()
                .toList();

        Map<String, Long> skuToOfferId = resolveSkuToOfferId(workspaceId, skuCodes);

        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (CsvRow row : rows) {
            Long offerId = skuToOfferId.get(row.skuCode());
            if (offerId == null) {
                skipped++;
                errors.add("SKU not found: " + row.skuCode());
                continue;
            }

            try {
                CompetitorMatchEntity match = findOrCreateMatch(
                        workspaceId, offerId, row.competitorName(), userId);

                var obs = new CompetitorObservationEntity();
                obs.setCompetitorMatchId(match.getId());
                obs.setCompetitorPrice(row.competitorPrice());
                obs.setObservedAt(OffsetDateTime.now());
                observationRepository.save(obs);
                created++;
            } catch (Exception e) {
                skipped++;
                errors.add("SKU %s: %s".formatted(row.skuCode(), e.getMessage()));
            }
        }

        log.info("Bulk competitor upload: workspace={}, created={}, skipped={}",
                workspaceId, created, skipped);
        return new BulkUploadResult(rows.size(), created, skipped, errors);
    }

    private CompetitorMatchEntity findOrCreateMatch(long workspaceId, long offerId,
                                                    String competitorName, long userId) {
        List<CompetitorMatchEntity> existing =
                matchRepository.findAllByWorkspaceIdAndMarketplaceOfferId(workspaceId, offerId);

        for (CompetitorMatchEntity m : existing) {
            if (competitorName != null && competitorName.equals(m.getCompetitorName())) {
                return m;
            }
        }

        return createMatch(workspaceId, offerId, competitorName, null, userId);
    }

    private Map<String, Long> resolveSkuToOfferId(long workspaceId, List<String> skuCodes) {
        List<PricingDataReadRepository.OfferRow> offers =
                dataReadRepository.findOffersBySkuCodes(workspaceId, skuCodes);
        return offers.stream()
                .collect(Collectors.toMap(
                        o -> String.valueOf(o.sellerSkuId()),
                        PricingDataReadRepository.OfferRow::id,
                        (a, b) -> a));
    }

    List<CsvRow> parseCsv(InputStream inputStream) {
        List<CsvRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().contains("sku_code")
                            || line.toLowerCase().contains("competitor_name")) {
                        continue;
                    }
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split(",", -1);
                if (parts.length < 3) {
                    continue;
                }
                String skuCode = parts[0].trim();
                String competitorName = parts[1].trim();
                String priceStr = parts[2].trim();

                if (skuCode.isEmpty() || priceStr.isEmpty()) {
                    continue;
                }

                try {
                    BigDecimal price = new BigDecimal(priceStr);
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        rows.add(new CsvRow(skuCode, competitorName, price));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV", e);
        }
        return rows;
    }

    public record CsvRow(String skuCode, String competitorName, BigDecimal competitorPrice) {
    }

    public record BulkUploadResult(int totalRows, int created, int skipped,
                                   List<String> errors) {
    }
}
