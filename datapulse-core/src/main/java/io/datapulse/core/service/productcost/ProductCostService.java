package io.datapulse.core.service.productcost;

import static io.datapulse.domain.MessageCodes.ACCOUNT_ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_ACCOUNT_IMMUTABLE;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_FIELD_EMPTY;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_FIELD_NOT_DATE;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_FIELD_NOT_INTEGER;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_FIELD_NOT_NUMBER;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_READ_FAILED;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_PRODUCT_BY_SOURCE_ID_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_PRODUCT_IMMUTABLE;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_VALID_FROM_REQUIRED;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_VALUE_REQUIRED;
import static io.datapulse.domain.MessageCodes.PRODUCT_ID_REQUIRED;
import static io.datapulse.domain.ValidationKeys.DTO_REQUIRED;
import static io.datapulse.domain.ValidationKeys.REQUEST_REQUIRED;

import io.datapulse.core.entity.productcost.ProductCostEntity;
import io.datapulse.core.excel.ExcelStreamingReader;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.productcost.ProductCostRepository;
import io.datapulse.core.service.AbstractCrudService;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.productcost.ProductCostDto;
import io.datapulse.domain.exception.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
@RequiredArgsConstructor
public class ProductCostService extends AbstractCrudService<ProductCostDto, ProductCostEntity> {

  private static final int SHEET_INDEX = 0;
  private static final int BATCH_SIZE = 100;

  private final MapperFacade mapperFacade;
  private final ProductCostRepository productCostRepository;
  private final ProductIdentifierResolver productIdentifierResolver;

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected JpaRepository<ProductCostEntity, Long> repository() {
    return productCostRepository;
  }

  @Override
  protected Class<ProductCostDto> dtoType() {
    return ProductCostDto.class;
  }

  @Override
  protected Class<ProductCostEntity> entityType() {
    return ProductCostEntity.class;
  }

  @Override
  protected void validateOnCreate(
      @Valid
      @NotNull(message = DTO_REQUIRED)
      ProductCostDto dto
  ) {
    if (dto.getAccountId() == null) {
      throw new BadRequestException(ACCOUNT_ID_REQUIRED);
    }
    if (dto.getProductId() == null) {
      throw new BadRequestException(PRODUCT_ID_REQUIRED);
    }
    if (dto.getCostValue() == null) {
      throw new BadRequestException(PRODUCT_COST_VALUE_REQUIRED);
    }
    if (dto.getValidFrom() == null) {
      throw new BadRequestException(PRODUCT_COST_VALID_FROM_REQUIRED);
    }
  }

  @Override
  protected void validateOnUpdate(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id,

      @Valid
      @NotNull(message = DTO_REQUIRED)
      ProductCostDto dto,

      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      ProductCostEntity existing
  ) {
    Long existingAccountId = existing.getAccountId();
    Long existingProductId = existing.getProductId();

    Long dtoAccountId = dto.getAccountId();
    Long dtoProductId = dto.getProductId();

    if (dtoAccountId != null && !dtoAccountId.equals(existingAccountId)) {
      throw new BadRequestException(PRODUCT_COST_ACCOUNT_IMMUTABLE);
    }
    if (dtoProductId != null && !dtoProductId.equals(existingProductId)) {
      throw new BadRequestException(PRODUCT_COST_PRODUCT_IMMUTABLE);
    }
  }

  @Override
  protected ProductCostEntity merge(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      ProductCostEntity target,

      @Valid
      @NotNull(message = DTO_REQUIRED)
      ProductCostDto source
  ) {
    if (source.getCostValue() != null) {
      target.setCostValue(source.getCostValue());
    }
    if (source.getCurrency() != null) {
      target.setCurrency(source.getCurrency());
    }
    if (source.getValidFrom() != null) {
      target.setValidFrom(source.getValidFrom());
    }
    target.setValidTo(source.getValidTo());
    return target;
  }

  @Override
  protected ProductCostEntity beforeSave(ProductCostEntity entity) {
    OffsetDateTime now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected ProductCostEntity beforeUpdate(ProductCostEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Transactional
  public void importFromExcel(
      @NotNull(message = REQUEST_REQUIRED)
      MultipartFile file
  ) {
    try (InputStream inputStream = file.getInputStream()) {
      List<ProductCostDto> batch = new ArrayList<>(BATCH_SIZE);

      ExcelStreamingReader.readSheet(inputStream, SHEET_INDEX, row -> {
        if (isHeaderRow(row)) {
          return;
        }
        if (isEmptyRow(row)) {
          return;
        }

        ProductCostDto dto = mapRowToDto(row);
        batch.add(dto);

        if (batch.size() >= BATCH_SIZE) {
          processBatch(batch);
          batch.clear();
        }
      });

      if (!batch.isEmpty()) {
        processBatch(batch);
      }
    } catch (IOException ex) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_READ_FAILED, ex.getMessage());
    }
  }

  private void processBatch(List<ProductCostDto> batch) {
    List<ProductCostEntity> entitiesToSave = new ArrayList<>(batch.size());

    for (ProductCostDto dto : batch) {
      validateOnCreate(dto);

      productCostRepository.findByAccountIdAndProductIdAndValidFrom(
              dto.getAccountId(),
              dto.getProductId(),
              dto.getValidFrom()
          )
          .ifPresentOrElse(existing -> {
            ProductCostEntity merged = merge(existing, dto);
            ProductCostEntity updated = beforeUpdate(merged);
            entitiesToSave.add(updated);
          }, () -> {
            ProductCostEntity created = mapper().to(dto, ProductCostEntity.class);
            ProductCostEntity withAudit = beforeSave(created);
            entitiesToSave.add(withAudit);
          });
    }

    if (!entitiesToSave.isEmpty()) {
      productCostRepository.saveAll(entitiesToSave);
    }
  }

  private boolean isHeaderRow(List<String> row) {
    if (row.isEmpty()) {
      return false;
    }
    String firstCell = normalize(row.get(0));
    String secondCell = row.size() > 1 ? normalize(row.get(1)) : "";
    return firstCell.contains("account") && secondCell.contains("source");
  }

  private boolean isEmptyRow(List<String> row) {
    return row.stream()
        .allMatch(value -> value == null || value.isBlank());
  }

  private ProductCostDto mapRowToDto(List<String> row) {
    String accountIdRaw = getCell(row, 0);
    String sourceProductIdRaw = getCell(row, 1);
    String costValueRaw = getCell(row, 2);
    String currencyRaw = getCell(row, 3);
    String validFromRaw = getCell(row, 4);
    String validToRaw = getCell(row, 5);

    Long accountId = parseLong(accountIdRaw, "account_id");
    String sourceProductId = parseSourceProductId(sourceProductIdRaw);
    Long productId = resolveProductId(accountId, sourceProductId);
    BigDecimal costValue = parseBigDecimal(costValueRaw);
    String currency = currencyRaw.isBlank() ? "RUB" : currencyRaw.trim();
    LocalDate validFrom = parseDate(validFromRaw, "valid_from");
    LocalDate validTo = validToRaw.isBlank() ? null : parseDate(validToRaw, "valid_to");

    ProductCostDto dto = new ProductCostDto();
    dto.setAccountId(accountId);
    dto.setProductId(productId);
    dto.setCostValue(costValue);
    dto.setCurrency(currency);
    dto.setValidFrom(validFrom);
    dto.setValidTo(validTo);

    return dto;
  }

  private String getCell(List<String> row, int index) {
    if (index >= row.size()) {
      return "";
    }
    String value = row.get(index);
    return value == null ? "" : value.trim();
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase();
  }

  private Long parseLong(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_EMPTY, fieldName);
    }
    try {
      if (raw.endsWith(".0")) {
        raw = raw.substring(0, raw.length() - 2);
      }
      return Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_NOT_INTEGER, fieldName, raw);
    }
  }

  private String parseSourceProductId(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_EMPTY, "source_product_id");
    }
    return raw.trim();
  }

  private Long resolveProductId(Long accountId, String sourceProductId) {
    Long productId = productIdentifierResolver.resolveProductId(accountId, sourceProductId);
    if (productId == null) {
      throw new BadRequestException(
          PRODUCT_COST_PRODUCT_BY_SOURCE_ID_NOT_FOUND,
          accountId,
          sourceProductId
      );
    }
    return productId;
  }

  private BigDecimal parseBigDecimal(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_EMPTY, "cost_value");
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      throw new BadRequestException(
          PRODUCT_COST_EXCEL_FIELD_NOT_NUMBER,
          "cost_value",
          raw
      );
    }
  }

  private LocalDate parseDate(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_EMPTY, fieldName);
    }
    try {
      return LocalDate.parse(raw);
    } catch (Exception ex) {
      throw new BadRequestException(
          PRODUCT_COST_EXCEL_FIELD_NOT_DATE,
          fieldName,
          raw
      );
    }
  }
}
