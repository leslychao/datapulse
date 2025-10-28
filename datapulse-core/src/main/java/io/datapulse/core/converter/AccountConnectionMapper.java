package io.datapulse.core.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.codec.CredentialsCodec;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = MapStructCentralConfig.class)
public interface AccountConnectionMapper
    extends BeanConverter<AccountConnectionDto, AccountConnectionEntity> {

  @Override
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "account.id", source = "accountId")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountConnectionEntity mapToEntity(AccountConnectionDto dto);

  @Override
  @Mapping(source = "account.id", target = "accountId")
  AccountConnectionDto mapToDto(AccountConnectionEntity entity);

  @Mapping(
      target = "maskedCredentials",
      expression = "java(maskCredentials(dto, cryptoService, objectMapper, credentialsCodec))"
  )
  AccountConnectionResponse toResponse(
      AccountConnectionDto dto,
      CryptoService cryptoService,
      ObjectMapper objectMapper,
      CredentialsCodec credentialsCodec);

  default AccountConnectionDto fromCreateRequest(
      AccountConnectionCreateRequest request,
      CryptoService cryptoService,
      ObjectMapper objectMapper
  ) {
    AccountConnectionDto dto = new AccountConnectionDto();
    dto.setAccountId(request.accountId());
    dto.setMarketplace(request.marketplace());
    dto.setActive(request.active() == null ? Boolean.TRUE : request.active());
    if (request.credentials() != null) {
      dto.setCredentialsEncrypted(
          cryptoService.encrypt(writeJson(request.credentials(), objectMapper)));
    }
    return dto;
  }

  @Mapping(target = "account", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void applyUpdateFromDto(AccountConnectionDto dto, @MappingTarget AccountConnectionEntity entity);

  default void applyUpdate(
      AccountConnectionUpdateRequest request,
      @MappingTarget AccountConnectionDto dto,
      CryptoService cryptoService,
      ObjectMapper objectMapper) {
    if (request.marketplace() != null) {
      dto.setMarketplace(request.marketplace());
    }
    if (request.active() != null) {
      dto.setActive(request.active());
    }
    if (request.credentials() != null) {
      dto.setCredentialsEncrypted(
          cryptoService.encrypt(writeJson(request.credentials(), objectMapper)));
    }
  }

  default String maskCredentials(
      AccountConnectionDto dto,
      CryptoService cryptoService,
      ObjectMapper objectMapper,
      CredentialsCodec credentialsCodec) {
    String encrypted = dto.getCredentialsEncrypted();
    if (encrypted == null) {
      return null;
    }
    try {
      String json = cryptoService.decrypt(encrypted);

      Class<? extends MarketplaceCredentials> target;
      switch (dto.getMarketplace()) {
        case WILDBERRIES -> target = WbCredentials.class;
        case OZON -> target = OzonCredentials.class;
        default -> {
          return null;
        }
      }

      MarketplaceCredentials marketplaceCredentials = objectMapper.readValue(json, target);
      return credentialsCodec.mask(dto.getMarketplace(), marketplaceCredentials);

    } catch (JsonProcessingException e) {
      throw new AppException(e, MessageCodes.CREDENTIALS_DESERIALIZATION_ERROR);
    }
  }

  private <T> String writeJson(T object, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException ex) {
      throw new AppException(ex, MessageCodes.CREDENTIALS_JSON_SERIALIZATION_ERROR);
    }
  }
}
