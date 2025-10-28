package io.datapulse.core.service.crypto;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AesGcmCryptoService implements CryptoService {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String VERSION = "v1";
  private static final int TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private final SecretKey masterKey;
  private final SecureRandom random;

  @Override
  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return VERSION + ":" + b64(iv) + ":" + b64(ct);
    } catch (Exception e) {
      throw new AppException(e, MessageCodes.CRYPTO_ENCRYPTION_ERROR);
    }
  }

  @Override
  public String decrypt(String cipherText) {
    try {
      String[] parts = cipherText.split(":", 3);
      if (parts.length != 3) {
        throw new AppException(MessageCodes.CRYPTO_DECRYPTION_INVALID_FORMAT);
      }
      if (!VERSION.equals(parts[0])) {
        throw new AppException(MessageCodes.CRYPTO_DECRYPTION_UNSUPPORTED_VERSION, parts[0]);
      }
      byte[] iv = b64d(parts[1]);
      if (iv.length != IV_BYTES) {
        throw new AppException(MessageCodes.CRYPTO_DECRYPTION_INVALID_IV_LENGTH, iv.length);
      }
      byte[] ct = b64d(parts[2]);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
      byte[] pt = cipher.doFinal(ct);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AppException(e, MessageCodes.CRYPTO_DECRYPTION_ERROR);
    }
  }

  private static String b64(byte[] b) {
    return Base64.getEncoder().encodeToString(b);
  }

  private static byte[] b64d(String s) {
    return Base64.getDecoder().decode(s);
  }
}
