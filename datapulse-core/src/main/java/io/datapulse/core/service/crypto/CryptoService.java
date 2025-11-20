package io.datapulse.core.service.crypto;

public interface CryptoService {

  String encrypt(String plainText);

  String decrypt(String cipherText);
}
