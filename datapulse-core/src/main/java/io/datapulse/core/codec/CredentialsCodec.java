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

  private static final int WB_TOKEN_TAIL = 4;
  private static final int OZON_API_KEY_TAIL = 4;
  private static final int OZON_CLIENT_ID_TAIL = 3;

  private static final Map<MarketplaceType, MaskStrategy> MASKERS = new EnumMap<>(
      MarketplaceType.class);

  static {
    MASKERS.put(MarketplaceType.WILDBERRIES, creds -> {
      if (!(creds instanceof WbCredentials wb)) {
        return "***";
      }
      return "WB{token=" + tail(wb.token(), WB_TOKEN_TAIL) + "}";
    });
    MASKERS.put(MarketplaceType.OZON, creds -> {
      if (!(creds instanceof OzonCredentials oz)) {
        return "***";
      }
      return "OZON{apiKey=" + tail(oz.apiKey(), OZON_API_KEY_TAIL)
          + ", clientId=" + tail(oz.clientId(), OZON_CLIENT_ID_TAIL) + "}";
    });
  }

  public String mask(MarketplaceType type, MarketplaceCredentials creds) {
    return Optional.ofNullable(MASKERS.get(type))
        .map(strategy -> strategy.mask(creds))
        .orElse("***");
  }

  private static String tail(String value, int visibleChars) {
    if (value == null || value.isEmpty()) {
      return "***";
    }
    int len = value.length();
    int tailLen = Math.min(visibleChars, len);
    return "***" + value.substring(len - tailLen);
  }
}
