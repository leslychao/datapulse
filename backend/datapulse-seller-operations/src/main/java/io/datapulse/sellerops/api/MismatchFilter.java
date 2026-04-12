package io.datapulse.sellerops.api;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Query-parameter filter for mismatch list / export.
 * Frontend may send comma-separated values for list fields
 * (e.g. {@code type=PRICE,STOCK}); {@link #normalize()} splits them.
 */
public record MismatchFilter(
    List<String> type,
    List<String> sourcePlatform,
    List<String> status,
    List<String> severity,
    LocalDate from,
    LocalDate to,
    String query,
    Long offerId
) {

  public MismatchFilter normalize() {
    return new MismatchFilter(
        splitCsv(type),
        splitCsv(sourcePlatform),
        splitCsv(status),
        splitCsv(severity),
        from, to, query, offerId
    );
  }

  private static List<String> splitCsv(List<String> input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return input.stream()
        .flatMap(s -> Arrays.stream(s.split(",")))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }
}

