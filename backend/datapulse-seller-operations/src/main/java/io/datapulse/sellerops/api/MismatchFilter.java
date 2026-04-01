package io.datapulse.sellerops.api;

import java.time.LocalDate;
import java.util.List;

public record MismatchFilter(
    List<String> type,
    List<Long> connectionId,
    List<String> status,
    List<String> severity,
    LocalDate from,
    LocalDate to,
    String query,
    Long offerId
) {
}
