package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_INVALID_JSON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultCredentialsProvider implements CredentialsProvider {

  private final AccountConnectionService accountConnectionService;
  private final CryptoService cryptoService;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(readOnly = true)
  public MarketplaceCredentials resolve(long accountId, MarketplaceType type) {
    var entityOpt = accountConnectionService
        .getByAccountIdAndMarketplaceType(accountId, type);
    String decrypted = cryptoService.decrypt(entityOpt.getCredentialsEncrypted());
    try {
      return switch (type) {
        case WILDBERRIES -> objectMapper.readValue(decrypted, WbCredentials.class);
        case OZON -> objectMapper.readValue(decrypted, OzonCredentials.class);
      };
    } catch (JsonProcessingException e) {
      throw new AppException(ACCOUNT_CONNECTION_INVALID_JSON, e);
    }
  }
}
