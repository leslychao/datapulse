package io.datapulse.core.service;

import io.datapulse.core.converter.AccountMapper;
import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.repository.AccountRepository;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  protected AccountEntity updateEntityWithDto(
      @NonNull AccountEntity entity,
      @NonNull AccountDto dto) {
    entity.setName(dto.getName());
    entity.setUpdatedAt(OffsetDateTime.now());
    return entity;
  }

  @Transactional
  public AccountResponse create(@NonNull AccountCreateRequest req) {
    AccountDto dto = mapper.fromCreateRequest(req);
    AccountDto saved = save(dto);
    return mapper.toResponse(saved);
  }

  public boolean exists(Long id) {
    Optional.ofNullable(id).orElseThrow(() -> new BadRequestException("account.id.notBlank"));
    return getRepository().existsById(id);
  }
}
