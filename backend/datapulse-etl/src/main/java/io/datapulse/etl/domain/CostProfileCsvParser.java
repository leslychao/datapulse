package io.datapulse.etl.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.api.BulkImportResponse.BulkImportError;
import lombok.Getter;

public final class CostProfileCsvParser {

    private static final int EXPECTED_COLUMNS = 4;

    private CostProfileCsvParser() {
    }

    public static ParseResult parse(InputStream inputStream) throws IOException {
        var rows = new ArrayList<CsvRow>();
        var errors = new ArrayList<BulkImportError>();
        int lineNumber = 0;

        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (lineNumber == 1 && isHeaderLine(line)) {
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length != EXPECTED_COLUMNS) {
                    errors.add(new BulkImportError(lineNumber, "row",
                            "Expected %d columns, got %d".formatted(EXPECTED_COLUMNS, parts.length)));
                    continue;
                }

                String skuCode = parts[0].trim();
                if (skuCode.isEmpty()) {
                    errors.add(new BulkImportError(lineNumber, "sku_code", "sku_code is required"));
                    continue;
                }

                BigDecimal costPrice = parseCostPrice(parts[1].trim(), lineNumber, errors);
                if (costPrice == null) {
                    continue;
                }

                String currency = parts[2].trim();
                if (currency.isEmpty()) {
                    errors.add(new BulkImportError(lineNumber, "currency", "currency is required"));
                    continue;
                }

                LocalDate validFrom = parseValidFrom(parts[3].trim(), lineNumber, errors);
                if (validFrom == null) {
                    continue;
                }

                rows.add(new CsvRow(lineNumber, skuCode, costPrice, currency, validFrom));
            }
        }

        return new ParseResult(rows, errors);
    }

    private static boolean isHeaderLine(String line) {
        String lower = line.toLowerCase().trim();
        return lower.startsWith("sku_code") || lower.startsWith("sku");
    }

    private static BigDecimal parseCostPrice(String value, int lineNumber,
                                             List<BulkImportError> errors) {
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new BulkImportError(lineNumber, "cost_price", "cost_price must be > 0"));
                return null;
            }
            return price;
        } catch (NumberFormatException e) {
            errors.add(new BulkImportError(lineNumber, "cost_price",
                    "Invalid number: '%s'".formatted(value)));
            return null;
        }
    }

    private static LocalDate parseValidFrom(String value, int lineNumber,
                                            List<BulkImportError> errors) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(new BulkImportError(lineNumber, "valid_from",
                    "Invalid date (expected yyyy-MM-dd): '%s'".formatted(value)));
            return null;
        }
    }

    @Getter
    public static class ParseResult {
        private final List<CsvRow> rows;
        private final List<BulkImportError> errors;

        ParseResult(List<CsvRow> rows, List<BulkImportError> errors) {
            this.rows = rows;
            this.errors = errors;
        }
    }

    public record CsvRow(
            int lineNumber,
            String skuCode,
            BigDecimal costPrice,
            String currency,
            LocalDate validFrom
    ) {
    }
}
