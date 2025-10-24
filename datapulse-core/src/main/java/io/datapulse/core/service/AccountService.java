package io.datapulse.core.service;

import io.datapulse.core.converter.AccountMapper;
import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.repository.AccountRepository;
import io.datapulse.domain.dto.AccountDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AccountService extends AbstractCrudService<AccountDto, AccountEntity> {

  private final AccountRepository repository;
  private final AccountMapper mapper;

  @Override
  protected BeanConverter<AccountDto, AccountEntity> getConverter() {
    return mapper;
  }

  @Override
  protected JpaRepository<AccountEntity, Long> getRepository() {
    return repository;
  }

  @Override
  protected AccountEntity updateEntityWithDto(AccountEntity entity, AccountDto dto) {
    return null;
  }
}
