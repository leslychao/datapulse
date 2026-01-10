package io.datapulse.core.mapper;

import io.datapulse.core.codec.CredentialsCodec;
import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.service.vault.MarketplaceCredentialsVaultService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    uses = TimeMapper.class,
    config = BaseMapperConfig.class
)
public abstract class AccountConnectionMapper {

  @Autowired
  protected CredentialsCodec credentialsCodec;

  @Autowired
  protected MarketplaceCredentialsVaultService vaultService;

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "account.id", source = "accountId")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionEntity toEntity(AccountConnectionDto dto);

  @Mapping(source = "account.id", target = "accountId")
  @Mapping(target = "credentials", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionUpdateRequest request);

  @Mapping(target = "maskedCredentials", expression = "java(maskCredentials(dto))")
  public abstract AccountConnectionResponse toResponse(AccountConnectionDto dto);

  protected String maskCredentials(AccountConnectionDto dto) {
    MarketplaceType marketplace = dto.getMarketplace();
    MarketplaceCredentials credentials = dto.getCredentials();

    if (marketplace == null || credentials == null) {
      return null;
    }

    return credentialsCodec.mask(marketplace, credentials);
  }
}
