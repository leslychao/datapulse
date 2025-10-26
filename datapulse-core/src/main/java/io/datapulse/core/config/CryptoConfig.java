package io.datapulse.core.config;

import io.datapulse.domain.exception.AppException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

  @Bean
  public SecretKey masterKey(CryptoProperties props) {
    String encodedKey = props.getMasterKeyBase64();

    if (encodedKey == null || encodedKey.isBlank()) {
      throw new AppException("crypto.master-key.missing");
    }

    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(encodedKey);
    } catch (IllegalArgumentException e) {
      throw new AppException(e, "crypto.master-key.invalid-base64");
    }

    int len = raw.length;
    if (len != 16 && len != 24 && len != 32) {
      throw new AppException("crypto.master-key.invalid-length", len);
    }

    return new SecretKeySpec(raw, "AES");
  }

  @Bean
  public SecureRandom secureRandom() {
    return new SecureRandom();
  }
}
