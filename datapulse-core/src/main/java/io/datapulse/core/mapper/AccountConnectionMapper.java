package io.datapulse.core.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.codec.CredentialsCodec;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.core.util.JsonUtils;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    uses = TimeMapper.class,
    config = BaseMapperConfig.class
)
public abstract class AccountConnectionMapper {

  @Autowired
  protected CryptoService cryptoService;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected CredentialsCodec credentialsCodec;

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "account.id", source = "accountId")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionEntity mapToEntity(AccountConnectionDto dto);

  @Mapping(source = "account.id", target = "accountId")
  public abstract AccountConnectionDto mapToDto(AccountConnectionEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "marketplace", source = "marketplace")
  @Mapping(target = "active", source = "active")
  @Mapping(target = "credentialsEncrypted", source = "credentials", qualifiedByName = "encryptCredentials")
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto mapCreateRequestToDto(
      AccountConnectionCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "marketplace", source = "marketplace")
  @Mapping(target = "active", source = "active")
  @Mapping(target = "credentialsEncrypted", source = "credentials", qualifiedByName = "encryptCredentials")
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto mapUpdateRequestToDto(
      AccountConnectionUpdateRequest request);

  @Mapping(target = "maskedCredentials", expression = "java(maskCredentials(dto))")
  public abstract AccountConnectionResponse mapToResponse(AccountConnectionDto dto);

  @Named("encryptCredentials")
  protected String encryptCredentials(MarketplaceCredentials credentials) {
    if (credentials == null) {
      return null;
    }
    return cryptoService.encrypt(JsonUtils.writeJson(credentials, objectMapper));
  }

  protected MarketplaceCredentials readDecryptedCredentials(AccountConnectionDto dto) {
    String encrypted = dto.getCredentialsEncrypted();
    if (encrypted == null) {
      return null;
    }
    Class<? extends MarketplaceCredentials> type = resolveCredentialsClass(dto.getMarketplace());
    if (type == null) {
      return null;
    }
    String json = cryptoService.decrypt(encrypted);
    return JsonUtils.readJson(json, type, objectMapper);
  }

  protected String maskCredentials(AccountConnectionDto dto) {
    MarketplaceCredentials credentials = readDecryptedCredentials(dto);
    if (credentials == null) {
      return null;
    }
    return credentialsCodec.mask(dto.getMarketplace(), credentials);
  }

  protected static Class<? extends MarketplaceCredentials> resolveCredentialsClass(
      MarketplaceType marketplace) {
    if (marketplace == null) {
      return null;
    }
    return switch (marketplace) {
      case WILDBERRIES -> WbCredentials.class;
      case OZON -> OzonCredentials.class;
    };
  }
}
