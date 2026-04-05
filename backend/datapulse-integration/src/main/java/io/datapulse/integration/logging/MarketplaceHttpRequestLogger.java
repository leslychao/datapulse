package io.datapulse.integration.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.integration.config.MarketplaceHttpLoggingProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Optional verbose logging for outbound marketplace HTTP (ETL adapters). When enabled, logs full
 * request URI (including query string) and JSON body for POST — where Ozon/WB usually encode
 * pagination (offset, cursor, last_id, page, etc.). Headers and tokens are never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceHttpRequestLogger {

  private final MarketplaceHttpLoggingProperties properties;
  private final ObjectMapper objectMapper;

  public void logRequest(
      String marketplace,
      HttpMethod method,
      URI uri,
      long connectionId,
      RateLimitGroup rateLimitGroup,
      Object body) {
    if (!properties.enabled()) {
      return;
    }
    String bodyPreview = formatBody(body);
    log.info(
        "Marketplace HTTP request: marketplace={}, method={}, uri={}, connectionId={},"
            + " rateLimitGroup={}, body={}",
        marketplace,
        method,
        uri,
        connectionId,
        rateLimitGroup,
        bodyPreview);
  }

  private String formatBody(Object body) {
    if (body == null) {
      return "-";
    }
    try {
      String raw =
          body instanceof String str ? str : objectMapper.writeValueAsString(body);
      raw = raw.replaceAll("\\s+", " ").trim();
      int configured = properties.maxBodyChars();
      int max = configured > 0 ? Math.min(configured, 1024 * 1024) : 8192;
      if (raw.length() <= max) {
        return raw;
      }
      return raw.substring(0, max) + "...[truncated, maxBodyChars=%d]".formatted(max);
    } catch (JsonProcessingException e) {
      return body.getClass().getSimpleName() + " (serialization failed: " + e.getMessage() + ")";
    }
  }
}
