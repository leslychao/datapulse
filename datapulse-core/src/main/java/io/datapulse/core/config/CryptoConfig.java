package io.datapulse.core.config;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CryptoConfig {

  private final CryptoProperties props;

  @Bean
  public SecretKey masterKey() {
    String encoded = props.getMasterKeyBase64();

    if (encoded == null || encoded.isBlank()) {
      throw new AppException(MessageCodes.CRYPTO_MASTER_KEY_MISSING);
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new AppException(e, MessageCodes.CRYPTO_MASTER_KEY_INVALID_BASE64);
    }

    int length = decoded.length;
    if (length != 16 && length != 24 && length != 32) {
      throw new AppException(MessageCodes.CRYPTO_MASTER_KEY_INVALID_LENGTH, length);
    }

    return new SecretKeySpec(decoded, "AES");
  }

  @Bean
  public SecureRandom secureRandom() {
    return new SecureRandom();
  }
}
