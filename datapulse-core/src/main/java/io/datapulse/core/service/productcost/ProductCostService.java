package io.datapulse.core.service.productcost;

import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_FIELD_EMPTY;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_FIELD_NOT_NUMBER;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_HEADER_INVALID;
import static io.datapulse.domain.MessageCodes.PRODUCT_COST_EXCEL_READ_FAILED;

import io.datapulse.core.excel.ExcelStreamingReader;
import io.datapulse.core.repository.productcost.ProductCostRepository;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.productcost.ProductCostDto;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.response.productcost.ProductCostImportResponse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
@RequiredArgsConstructor
public class ProductCostService {

  private static final int SHEET_INDEX = 0;
  private static final int BATCH_SIZE = 100;

  private static final String COL_SOURCE_PRODUCT_ID = "source_product_id";
  private static final String COL_COST_VALUE = "cost_value";
  private static final String COL_CURRENCY = "currency";

  private static final List<String> EXPECTED_HEADER = List.of(
      COL_SOURCE_PRODUCT_ID,
      COL_COST_VALUE,
      COL_CURRENCY
  );

  private final ProductIdentifierResolver productIdentifierResolver;
  private final ProductCostRepository productCostRepository;

  @Transactional
  public ProductCostImportResponse importFromExcel(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      Long accountId,
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED)
      MultipartFile file
  ) {
    Instant importTimestamp = Instant.now();

    List<ProductCostImportResponse.ProductCostNotFoundRow> notFound = new ArrayList<>();
    long[] importedRows = {0};

    try (InputStream inputStream = file.getInputStream()) {
      List<ProductCostDto> batch = new ArrayList<>(BATCH_SIZE);
      int[] rowIndex = {0};

      ExcelStreamingReader.readSheet(inputStream, SHEET_INDEX, row -> {
        int currentRowNumber = rowIndex[0] + 1;

        if (rowIndex[0]++ == 0) {
          validateHeaderRow(row);
          return;
        }

        if (isEmptyRow(row)) {
          return;
        }

        Optional<ProductCostDto> dto = parseRow(
            accountId,
            row,
            currentRowNumber,
            notFound);
        if (dto.isEmpty()) {
          return;
        }

        batch.add(dto.get());

        if (batch.size() >= BATCH_SIZE) {
          importedRows[0] += upsertBatchDeduplicated(accountId, batch, importTimestamp);
          batch.clear();
        }
      });

      if (!batch.isEmpty()) {
        importedRows[0] += upsertBatchDeduplicated(accountId, batch, importTimestamp);
      }

      return new ProductCostImportResponse(
          importedRows[0],
          notFound.size(),
          List.copyOf(notFound)
      );
    } catch (BadRequestException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_READ_FAILED, "io-error");
    } catch (Exception ex) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_READ_FAILED, "unexpected-error");
    }
  }

  private long upsertBatchDeduplicated(
      Long accountId,
      List<ProductCostDto> batch,
      Instant importTimestamp) {
    Map<Long, ProductCostDto> lastByProductId = new LinkedHashMap<>(batch.size());
    for (ProductCostDto dto : batch) {
      lastByProductId.put(dto.getProductId(), dto);
    }
    productCostRepository.upsertBatchFromDtos(
        accountId,
        new ArrayList<>(lastByProductId.values()),
        importTimestamp
    );
    return lastByProductId.size();
  }

  private Optional<ProductCostDto> parseRow(
      Long accountId,
      List<String> row,
      int rowNumber,
      List<ProductCostImportResponse.ProductCostNotFoundRow> notFound
  ) {
    String sourceProductIdRaw = getCell(row, 0);
    String costValueRaw = getCell(row, 1);
    String currencyRaw = getCell(row, 2);

    String sourceProductId = parseRequiredText(
        sourceProductIdRaw,
        rowNumber,
        COL_SOURCE_PRODUCT_ID);

    Optional<Long> productId = productIdentifierResolver.resolveProductId(
        accountId,
        sourceProductId);

    if (productId.isEmpty()) {
      notFound.add(
          new ProductCostImportResponse.ProductCostNotFoundRow(rowNumber, sourceProductId));
      return Optional.empty();
    }

    BigDecimal costValue = parseBigDecimal(costValueRaw, rowNumber, COL_COST_VALUE);

    String currency = currencyRaw.isBlank()
        ? "RUB"
        : currencyRaw.trim();

    ProductCostDto dto = new ProductCostDto();
    dto.setAccountId(accountId);
    dto.setProductId(productId.get());
    dto.setCostValue(costValue);
    dto.setCurrency(currency);
    return Optional.of(dto);
  }

  private void validateHeaderRow(List<String> row) {
    if (isHeaderRow(row)) {
      return;
    }

    String expected = String.join(", ", EXPECTED_HEADER);
    String actual = row == null
        ? ""
        : String.join(", ", row.stream().map(this::normalize).toList());

    throw new BadRequestException(PRODUCT_COST_EXCEL_HEADER_INVALID, expected, actual);
  }

  private boolean isHeaderRow(List<String> row) {
    if (row == null || row.size() < EXPECTED_HEADER.size()) {
      return false;
    }
    for (int i = 0; i < EXPECTED_HEADER.size(); i++) {
      String actual = normalize(row.get(i));
      if (!EXPECTED_HEADER.get(i).equals(actual)) {
        return false;
      }
    }
    return true;
  }

  private boolean isEmptyRow(List<String> row) {
    return row == null || row.stream().allMatch(value -> value == null || value.isBlank());
  }

  private String getCell(List<String> row, int index) {
    if (row == null || index >= row.size()) {
      return "";
    }
    String value = row.get(index);
    return value == null ? "" : value.trim();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private String parseRequiredText(String raw, int rowNumber, String columnName) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_EMPTY, rowNumber, columnName);
    }
    return raw.trim();
  }

  private BigDecimal parseBigDecimal(String raw, int rowNumber, String columnName) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_EMPTY, rowNumber, columnName);
    }
    try {
      return new BigDecimal(raw.trim());
    } catch (NumberFormatException ex) {
      throw new BadRequestException(PRODUCT_COST_EXCEL_FIELD_NOT_NUMBER, rowNumber, columnName,
          raw);
    }
  }
}
