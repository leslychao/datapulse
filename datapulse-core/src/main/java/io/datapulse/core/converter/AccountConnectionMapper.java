package io.datapulse.core.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountConnectionMapper
    extends BeanConverter<AccountConnectionDto, AccountConnectionEntity> {

  @Override
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "account.id", source = "accountId")
  @Mapping(
      target = "createdAt",
      expression =
          "java( dto.getCreatedAt() != null ? dto.getCreatedAt() : java.time.OffsetDateTime.now(io.datapulse.domain.CommonConstants.ZONE_ID_DEFAULT) )"
  )
  public abstract AccountConnectionEntity mapToEntity(AccountConnectionDto dto);

  @Override
  @Mapping(
      target = "accountId",
      expression = "java(entity.getAccount() != null ? entity.getAccount().getId() : null)"
  )
  AccountConnectionDto mapToDto(AccountConnectionEntity entity);

  default AccountConnectionDto fromCreateRequest(
      AccountConnectionCreateRequest req,
      CryptoService cryptoService,
      ObjectMapper objectMapper
  ) {
    AccountConnectionDto dto = new AccountConnectionDto();
    dto.setAccountId(req.accountId());
    dto.setMarketplace(req.marketplaceType());
    dto.setActive(Boolean.TRUE.equals(req.active()));
    dto.setCredentialsEncrypted(cryptoService.encrypt(writeJson(req.credentials(), objectMapper)));
    dto.setCreatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return dto;
  }

  default AccountConnectionResponse toResponse(AccountConnectionDto dto) {
    return new AccountConnectionResponse(
        dto.getId(),
        dto.getAccountId(),
        dto.getMarketplace(),
        dto.getActive(),
        dto.getLastSyncAt(),
        dto.getLastSyncStatus(),
        dto.getCreatedAt(),
        dto.getUpdatedAt()
    );
  }

  private <T> String writeJson(T object, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException ex) {
      throw new AppException(ex, MessageCodes.CREDENTIALS_JSON_SERIALIZATION_ERROR);
    }
  }
}
