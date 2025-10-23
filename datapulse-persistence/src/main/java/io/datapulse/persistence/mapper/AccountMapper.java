package io.datapulse.persistence.mapper;

import io.datapulse.domain.model.Account;
import io.datapulse.persistence.entity.AccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountMapper {

  @Mapping(target = "tokenEncrypted", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  AccountEntity toEntity(Account source);

  @Mapping(target = "tokenMasked", expression = "java(\"***\")")
  Account toDomain(AccountEntity entity);
}
