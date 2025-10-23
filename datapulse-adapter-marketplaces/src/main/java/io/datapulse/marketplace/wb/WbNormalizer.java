package io.datapulse.marketplace.wb;

import io.datapulse.domain.model.Sale;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

@Component
public class WbNormalizer {

  public List<Sale> normalizeSales(Long accountId, List<Map<String, Object>> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    return raw.stream().map(row -> Sale.builder()
        .accountId(accountId)
        .sku(ObjectUtils.defaultIfNull((String) row.get("sku"), ""))
        .date(LocalDate.parse((String) row.get("date")))
        .quantity(((Number) row.getOrDefault("qty", 0)).intValue())
        .revenue(((Number) row.getOrDefault("revenue", 0.0)).doubleValue())
        .cost(((Number) row.getOrDefault("cost", 0.0)).doubleValue())
        .margin(((Number) row.getOrDefault("margin", 0.0)).doubleValue())
        .build()).collect(Collectors.toList());
  }
}
