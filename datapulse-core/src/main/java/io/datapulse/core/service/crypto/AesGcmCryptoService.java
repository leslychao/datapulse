package io.datapulse.core.service.crypto;

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

  private final SecretKey masterKey;
  private final SecureRandom random = new SecureRandom();
  private static final String ALG = "AES/GCM/NoPadding";
  private static final int TAG_BITS = 128, IV_BYTES = 12;

  @Override
  public String encrypt(String plain) {
    try {
      byte[] iv = random.generateSeed(IV_BYTES);
      var c = Cipher.getInstance(ALG);
      c.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
      var ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return "v1:" + b64(iv) + ":" + b64(ct);
    } catch (Exception e) {
      throw new IllegalStateException("Ошибка шифрования", e);
    }
  }

  @Override
  public String decrypt(String cipher) {
    try {
      var p = cipher.split(":");
      byte[] iv = b64d(p[1]);
      byte[] ct = b64d(p[2]);
      var c = Cipher.getInstance(ALG);
      c.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
      return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Ошибка расшифровки", e);
    }
  }

  private static String b64(byte[] b) {
    return Base64.getEncoder().encodeToString(b);
  }

  private static byte[] b64d(String s) {
    return Base64.getDecoder().decode(s);
  }
}
