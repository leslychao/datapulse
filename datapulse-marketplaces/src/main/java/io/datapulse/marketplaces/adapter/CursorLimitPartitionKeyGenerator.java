package io.datapulse.marketplaces.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public final class CursorLimitPartitionKeyGenerator {

  private static final int SHORT_HASH_BYTES = 6;

  public String generate(String cursor, int limit) {
    String effectiveCursor = cursor == null ? "" : cursor;
    String cursorTag = effectiveCursor.isBlank() ? "start" : shortHash(effectiveCursor);
    return "cursor_%s_limit_%d".formatted(cursorTag, limit);
  }

  private static String shortHash(String value) {
    MessageDigest digest = sha256();
    byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

    StringBuilder hex = new StringBuilder(SHORT_HASH_BYTES * 2);
    for (int i = 0; i < SHORT_HASH_BYTES; i++) {
      hex.append(String.format("%02x", hash[i]));
    }
    return hex.toString();
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is not available in the runtime.", ex);
    }
  }
}
