package io.datapulse.etl.domain;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.etl.api.BulkFormulaCostRequest;
import io.datapulse.etl.api.BulkFormulaCostResponse;
import io.datapulse.etl.api.BulkImportResponse;
import io.datapulse.etl.api.BulkImportResponse.BulkImportError;
import io.datapulse.etl.api.BulkUpdateCostProfileRequest;
import io.datapulse.etl.api.BulkUpdateCostProfileResponse;
import io.datapulse.etl.api.CostProfileFilter;
import io.datapulse.etl.api.CostProfileResponse;
import io.datapulse.etl.api.CreateCostProfileRequest;
import io.datapulse.etl.api.SellerSkuSuggestionResponse;
import io.datapulse.etl.api.UpdateCostProfileRequest;
import io.datapulse.etl.domain.CostProfileCsvParser.CsvRow;
import io.datapulse.etl.domain.CostProfileCsvParser.ParseResult;
import io.datapulse.etl.persistence.canonical.CostProfileEntity;
import io.datapulse.etl.persistence.canonical.CostProfileRepository;
import io.datapulse.etl.persistence.canonical.CostProfileRow;
import io.datapulse.etl.persistence.canonical.SellerSkuReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostProfileService {

    private static final int MAX_BULK_ROWS = 10_000;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String REQUIRED_CURRENCY = "RUB";
    private static final int SKU_SUGGESTION_MIN_QUERY = 3;
    private static final int SKU_SUGGESTION_MAX_RESULTS = 30;

    private final CostProfileRepository costProfileRepository;
    private final SellerSkuReadRepository sellerSkuReadRepository;

    @Transactional(readOnly = true)
    public Page<CostProfileResponse> listCurrentProfiles(long workspaceId, CostProfileFilter filter,
                                                         Pageable pageable) {
        List<CostProfileRow> rows = costProfileRepository.findCurrentProfiles(
                workspaceId, filter.sellerSkuId(), filter.search(),
                pageable.getSort(), pageable.getPageSize(), pageable.getOffset());

        long total = costProfileRepository.countCurrentProfiles(
                workspaceId, filter.sellerSkuId(), filter.search());

        List<CostProfileResponse> content = rows.stream().map(this::toResponse).toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public List<SellerSkuSuggestionResponse> listSkuSuggestions(long workspaceId, String search) {
        if (!StringUtils.hasText(search)) {
            return List.of();
        }
        String trimmed = search.trim();
        if (trimmed.length() < SKU_SUGGESTION_MIN_QUERY) {
            return List.of();
        }
        var pattern = "%" + trimmed + "%";
        return sellerSkuReadRepository
                .searchByWorkspaceAndPattern(workspaceId, pattern, SKU_SUGGESTION_MAX_RESULTS)
                .stream()
                .map(r -> new SellerSkuSuggestionResponse(r.sellerSkuId(), r.skuCode(), r.productName()))
                .toList();
    }

    @Transactional
    public CostProfileResponse createProfile(CreateCostProfileRequest request, long userId) {
        validateCurrency(request.currency());

        var entity = new CostProfileEntity();
        entity.setSellerSkuId(request.sellerSkuId());
        entity.setCostPrice(request.costPrice());
        entity.setCurrency(request.currency());
        entity.setValidFrom(request.validFrom());
        entity.setUpdatedByUserId(userId);

        costProfileRepository.createVersion(entity);

        String skuCode = sellerSkuReadRepository.findSkuCodeById(request.sellerSkuId()).orElse("");
        String productName = sellerSkuReadRepository.findProductNameBySellerSkuId(request.sellerSkuId())
                .orElse("");

        log.info("Cost profile created: sellerSkuId={}, validFrom={}, costPrice={}, userId={}",
                request.sellerSkuId(), request.validFrom(), request.costPrice(), userId);

        return toResponse(entity, skuCode, productName);
    }

    @Transactional
    public BulkImportResponse bulkImport(MultipartFile file, long workspaceId, long userId) {
        validateFile(file);

        ParseResult parseResult;
        try {
            parseResult = CostProfileCsvParser.parse(file.getInputStream());
        } catch (IOException e) {
            throw BadRequestException.of(MessageCodes.COST_PROFILE_INVALID, e, "Failed to read CSV file");
        }

        List<CsvRow> parsedRows = parseResult.getRows();
        var errors = new ArrayList<>(parseResult.getErrors());

        if (parsedRows.size() > MAX_BULK_ROWS) {
            throw BadRequestException.of(MessageCodes.COST_PROFILE_BULK_TOO_LARGE,
                    parsedRows.size(), MAX_BULK_ROWS);
        }

        int imported = 0;
        int skipped = 0;

        for (CsvRow row : parsedRows) {
            if (!REQUIRED_CURRENCY.equalsIgnoreCase(row.currency())) {
                errors.add(new BulkImportError(row.lineNumber(), "currency",
                        "Only RUB currency supported, got: '%s'".formatted(row.currency())));
                skipped++;
                continue;
            }

            Optional<Long> sellerSkuId = sellerSkuReadRepository
                    .findBySkuCodeAndWorkspaceId(row.skuCode(), workspaceId)
                    .map(sku -> sku.getId());

            if (sellerSkuId.isEmpty()) {
                errors.add(new BulkImportError(row.lineNumber(), "sku_code",
                        "SKU not found: '%s'".formatted(row.skuCode())));
                skipped++;
                continue;
            }

            var entity = new CostProfileEntity();
            entity.setSellerSkuId(sellerSkuId.get());
            entity.setCostPrice(row.costPrice());
            entity.setCurrency(row.currency().toUpperCase());
            entity.setValidFrom(row.validFrom());
            entity.setUpdatedByUserId(userId);

            costProfileRepository.createVersion(entity);
            imported++;
        }

        log.info("Bulk import completed: imported={}, skipped={}, errors={}, userId={}",
                imported, skipped, errors.size(), userId);

        return new BulkImportResponse(imported, skipped, errors);
    }

    @Transactional
    public BulkUpdateCostProfileResponse bulkUpdate(BulkUpdateCostProfileRequest request,
                                                     long workspaceId, long userId) {
        int updated = 0;
        int created = 0;
        int errorCount = 0;

        for (BulkUpdateCostProfileRequest.Item item : request.items()) {
            try {
                Optional<Long> existingId = costProfileRepository
                        .findCurrentProfileId(item.sellerSkuId(), workspaceId);

                if (existingId.isPresent()) {
                    costProfileRepository.updateProfile(existingId.get(),
                            item.costPrice(), item.currency(), item.validFrom(), userId);
                    updated++;
                } else {
                    var entity = new CostProfileEntity();
                    entity.setSellerSkuId(item.sellerSkuId());
                    entity.setCostPrice(item.costPrice());
                    entity.setCurrency(item.currency());
                    entity.setValidFrom(item.validFrom());
                    entity.setUpdatedByUserId(userId);
                    costProfileRepository.createVersion(entity);
                    created++;
                }
            } catch (Exception e) {
                log.warn("Bulk update failed for sellerSkuId={}: {}", item.sellerSkuId(), e.getMessage());
                errorCount++;
            }
        }

        log.info("Bulk cost profile update completed: updated={}, created={}, errors={}, userId={}",
                updated, created, errorCount, userId);
        return new BulkUpdateCostProfileResponse(updated, created, errorCount);
    }

    @Transactional
    public BulkFormulaCostResponse bulkFormula(BulkFormulaCostRequest request,
                                               long workspaceId, long userId) {
        int updated = 0;
        int skipped = 0;
        var errors = new ArrayList<String>();

        for (Long sellerSkuId : request.sellerSkuIds()) {
            try {
                Optional<CostProfileEntity> current = costProfileRepository
                        .findCurrentBySkuAndWorkspace(sellerSkuId, workspaceId);

                BigDecimal newCostPrice;
                if (request.operation() == CostUpdateOperation.FIXED) {
                    newCostPrice = request.value();
                } else {
                    if (current.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    newCostPrice = applyFormula(
                            current.get().getCostPrice(), request.operation(), request.value());
                }

                if (newCostPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    skipped++;
                    continue;
                }

                var entity = new CostProfileEntity();
                entity.setSellerSkuId(sellerSkuId);
                entity.setCostPrice(newCostPrice);
                entity.setCurrency(REQUIRED_CURRENCY);
                entity.setValidFrom(request.validFrom());
                entity.setUpdatedByUserId(userId);

                costProfileRepository.createVersion(entity);
                updated++;
            } catch (Exception e) {
                log.warn("Bulk formula failed for sellerSkuId={}: {}",
                        sellerSkuId, e.getMessage());
                errors.add("sellerSkuId=%d: %s".formatted(sellerSkuId, e.getMessage()));
            }
        }

        log.info("Bulk formula completed: operation={}, updated={}, skipped={}, errors={}, userId={}",
                request.operation(), updated, skipped, errors.size(), userId);
        return new BulkFormulaCostResponse(updated, skipped, errors);
    }

    private BigDecimal applyFormula(BigDecimal current, CostUpdateOperation operation,
                                    BigDecimal value) {
        return switch (operation) {
            case FIXED -> value;
            case INCREASE_PCT -> current.multiply(
                    BigDecimal.ONE.add(
                            value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            case DECREASE_PCT -> current.multiply(
                    BigDecimal.ONE.subtract(
                            value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            case MULTIPLY -> current.multiply(value)
                    .setScale(2, RoundingMode.HALF_UP);
        };
    }

    @Transactional
    public CostProfileResponse updateProfile(long id, UpdateCostProfileRequest request,
                                             long workspaceId, long userId) {
        validateCurrency(request.currency());

        costProfileRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.COST_PROFILE_NOT_FOUND, id));

        costProfileRepository.updateProfile(id, request.costPrice(), request.currency(),
                request.validFrom(), userId);

        CostProfileRow updated = costProfileRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.COST_PROFILE_NOT_FOUND, id));

        log.info("Cost profile updated: id={}, costPrice={}, validFrom={}, userId={}",
                id, request.costPrice(), request.validFrom(), userId);

        return toResponse(updated);
    }

    @Transactional
    public void deleteProfile(long id, long workspaceId) {
        costProfileRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.COST_PROFILE_NOT_FOUND, id));

        costProfileRepository.deleteById(id);

        log.info("Cost profile deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(long workspaceId) {
        List<CostProfileRow> rows = costProfileRepository.findAllCurrentProfilesForExport(workspaceId);
        StringBuilder sb = new StringBuilder(rows.size() * 48 + 48);
        sb.append("sku_code,cost_price,currency,valid_from\n");
        for (CostProfileRow row : rows) {
            if (row.getSkuCode() == null || row.getCostPrice() == null
                    || row.getCurrency() == null || row.getValidFrom() == null) {
                log.warn("Skipping cost profile export row with incomplete data: id={}", row.getId());
                continue;
            }
            sb.append(row.getSkuCode().trim()).append(',');
            sb.append(row.getCostPrice().stripTrailingZeros().toPlainString()).append(',');
            sb.append(row.getCurrency().trim().toUpperCase()).append(',');
            sb.append(row.getValidFrom()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public List<CostProfileResponse> getHistory(long sellerSkuId) {
        String skuCode = sellerSkuReadRepository.findSkuCodeById(sellerSkuId).orElse("");
        String productName = sellerSkuReadRepository.findProductNameBySellerSkuId(sellerSkuId).orElse("");

        return costProfileRepository.findHistoryBySku(sellerSkuId).stream()
                .map(entity -> toResponse(entity, skuCode, productName))
                .toList();
    }

    private void validateCurrency(String currency) {
        if (!REQUIRED_CURRENCY.equalsIgnoreCase(currency)) {
            throw BadRequestException.of(MessageCodes.COST_PROFILE_INVALID,
                    "Only RUB currency supported");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw BadRequestException.of(MessageCodes.COST_PROFILE_INVALID, "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BadRequestException.of(MessageCodes.COST_PROFILE_BULK_TOO_LARGE,
                    file.getSize(), MAX_FILE_SIZE);
        }
    }

    private CostProfileResponse toResponse(CostProfileRow row) {
        long id = row.getId() != null ? row.getId() : 0L;
        String productName = row.getProductName() != null ? row.getProductName() : "";
        return new CostProfileResponse(
                id,
                row.getSellerSkuId(),
                row.getSkuCode(),
                productName,
                row.getCostPrice(),
                row.getCurrency(),
                row.getValidFrom(),
                row.getValidTo(),
                row.getUpdatedByUserId(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private CostProfileResponse toResponse(CostProfileEntity entity, String skuCode, String productName) {
        String safeSku = skuCode != null ? skuCode : "";
        String safeName = productName != null ? productName : "";
        return new CostProfileResponse(
                entity.getId() != null ? entity.getId() : 0L,
                entity.getSellerSkuId(),
                safeSku,
                safeName,
                entity.getCostPrice(),
                entity.getCurrency(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getUpdatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
