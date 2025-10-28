package io.datapulse.core.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", config = MapStructCentralConfig.class)
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

  AccountConnectionResponse toResponse(AccountConnectionDto dto);

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

  private <T> String writeJson(T object, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException ex) {
      throw new AppException(ex, MessageCodes.CREDENTIALS_JSON_SERIALIZATION_ERROR);
    }
  }
}
