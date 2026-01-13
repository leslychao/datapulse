package io.datapulse.core.codec;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CredentialsCodec {

  @FunctionalInterface
  interface MaskStrategy {

    String mask(MarketplaceCredentials creds);
  }

  private static final int HEAD_VISIBLE = 3;

  private static final Map<MarketplaceType, MaskStrategy> MASKERS =
      new EnumMap<>(MarketplaceType.class);

  static {
    MASKERS.put(MarketplaceType.WILDBERRIES, creds -> {
      if (!(creds instanceof WbCredentials wb)) {
        return "***";
      }
      return "WB{token=" + head(wb.token()) + "}";
    });

    MASKERS.put(MarketplaceType.OZON, creds -> {
      if (!(creds instanceof OzonCredentials oz)) {
        return "***";
      }
      return "OZON{apiKey=" + head(oz.apiKey())
          + ", clientId=" + head(oz.clientId()) + "}";
    });
  }

  public String mask(MarketplaceType type, MarketplaceCredentials creds) {
    return Optional.ofNullable(MASKERS.get(type))
        .map(strategy -> strategy.mask(creds))
        .orElse("***");
  }

  private static String head(String value) {
    if (value == null || value.isEmpty()) {
      return "***";
    }
    int len = value.length();
    int headLen = Math.min(CredentialsCodec.HEAD_VISIBLE, len);
    return value.substring(0, headLen) + "***";
  }
}
