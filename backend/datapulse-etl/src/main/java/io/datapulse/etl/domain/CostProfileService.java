package io.datapulse.etl.domain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.etl.api.BulkImportResponse;
import io.datapulse.etl.api.BulkImportResponse.BulkImportError;
import io.datapulse.etl.api.CostProfileFilter;
import io.datapulse.etl.api.CostProfileResponse;
import io.datapulse.etl.api.CreateCostProfileRequest;
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

    private final CostProfileRepository costProfileRepository;
    private final SellerSkuReadRepository sellerSkuReadRepository;

    @Transactional(readOnly = true)
    public Page<CostProfileResponse> listCurrentProfiles(long workspaceId, CostProfileFilter filter,
                                                         Pageable pageable) {
        List<CostProfileRow> rows = costProfileRepository.findCurrentProfiles(
                workspaceId, filter.sellerSkuId(), filter.search(),
                pageable.getPageSize(), pageable.getOffset());

        long total = costProfileRepository.countCurrentProfiles(
                workspaceId, filter.sellerSkuId(), filter.search());

        List<CostProfileResponse> content = rows.stream().map(this::toResponse).toList();
        return new PageImpl<>(content, pageable, total);
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

        String skuCode = sellerSkuReadRepository.findSkuCodeById(request.sellerSkuId())
                .orElse(null);

        log.info("Cost profile created: sellerSkuId={}, validFrom={}, costPrice={}, userId={}",
                request.sellerSkuId(), request.validFrom(), request.costPrice(), userId);

        return toResponse(entity, skuCode);
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
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Transactional(readOnly = true)
    public List<CostProfileResponse> getHistory(long sellerSkuId) {
        String skuCode = sellerSkuReadRepository.findSkuCodeById(sellerSkuId).orElse(null);

        return costProfileRepository.findHistoryBySku(sellerSkuId).stream()
                .map(entity -> toResponse(entity, skuCode))
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
        return new CostProfileResponse(
                row.getId(),
                row.getSellerSkuId(),
                row.getSkuCode(),
                row.getCostPrice(),
                row.getCurrency(),
                row.getValidFrom(),
                row.getValidTo(),
                row.getUpdatedByUserId(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private CostProfileResponse toResponse(CostProfileEntity entity, String skuCode) {
        return new CostProfileResponse(
                entity.getId() != null ? entity.getId() : 0L,
                entity.getSellerSkuId(),
                skuCode,
                entity.getCostPrice(),
                entity.getCurrency(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getUpdatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
