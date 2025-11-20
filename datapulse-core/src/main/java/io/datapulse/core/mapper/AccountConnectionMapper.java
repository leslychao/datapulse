package io.datapulse.core.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.codec.CredentialsCodec;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.core.util.JsonUtils;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import org.apache.commons.lang3.StringUtils;
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
  public abstract AccountConnectionEntity toEntity(AccountConnectionDto dto);

  @Mapping(source = "account.id", target = "accountId")
  public abstract AccountConnectionDto toDto(AccountConnectionEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "marketplace", source = "marketplace")
  @Mapping(target = "active", source = "active")
  @Mapping(target = "credentialsEncrypted", source = "credentials", qualifiedByName = "encryptCredentials")
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "marketplace", source = "marketplace")
  @Mapping(target = "active", source = "active")
  @Mapping(target = "credentialsEncrypted", source = "credentials", qualifiedByName = "encryptCredentials")
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionUpdateRequest request);

  @Mapping(target = "maskedCredentials", expression = "java(maskCredentials(dto))")
  public abstract AccountConnectionResponse toResponse(AccountConnectionDto dto);

  @Named("encryptCredentials")
  protected String encryptCredentials(MarketplaceCredentials credentials) {
    if (credentials == null) {
      return null;
    }
    try {
      return cryptoService.encrypt(JsonUtils.writeJson(credentials, objectMapper));
    } catch (JsonProcessingException e) {
      throw new AppException(e, MessageCodes.CREDENTIALS_SERIALIZATION_ERROR);
    }
  }

  protected MarketplaceCredentials decryptCredentials(AccountConnectionDto dto) {
    String encrypted = dto.getCredentialsEncrypted();
    if (StringUtils.isBlank(encrypted)) {
      throw new AppException(MessageCodes.DATA_CORRUPTED_CREDENTIALS_MISSING, dto.getId());
    }

    var marketplace = dto.getMarketplace();
    if (marketplace == null) {
      throw new BadRequestException(MessageCodes.MARKETPLACE_REQUIRED);
    }

    Class<? extends MarketplaceCredentials> type = resolveCredentialsClass(marketplace);
    if (type == null) {
      throw new BadRequestException(MessageCodes.UNKNOWN_MARKETPLACE, marketplace);
    }
    String json = cryptoService.decrypt(encrypted);
    try {
      return JsonUtils.readJson(json, type, objectMapper);
    } catch (JsonProcessingException e) {
      throw new AppException(e, MessageCodes.CREDENTIALS_DESERIALIZATION_ERROR);
    }
  }

  protected String maskCredentials(AccountConnectionDto dto) {
    MarketplaceCredentials credentials = decryptCredentials(dto);
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
