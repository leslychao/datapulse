package io.datapulse.core.service;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.EtlExecutionAuditRepository;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EtlExecutionAuditService {

  private final EtlExecutionAuditRepository repository;
  private final MapperFacade mapper;

  @Transactional(readOnly = true)
  public Optional<EtlExecutionAuditDto> get(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id
  ) {
    return repository.findById(id).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public List<EtlExecutionAuditDto> getAll() {
    return repository.findAll().stream().map(this::toDto).toList();
  }

  public EtlExecutionAuditDto save(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      EtlExecutionAuditDto dto
  ) {
    EtlExecutionAuditEntity entity = mapper.to(dto, EtlExecutionAuditEntity.class);
    entity.setCreatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));

    return toDto(repository.save(entity));
  }

  public EtlExecutionAuditDto update(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      EtlExecutionAuditDto dto
  ) {
    Long id = requireId(dto);

    EtlExecutionAuditEntity existing = repository.findById(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.NOT_FOUND, id));

    merge(existing, dto);

    return toDto(repository.save(existing));
  }

  public void delete(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id
  ) {
    if (repository.deleteByIdAndIdIsNotNull(id) == 0) {
      throw new NotFoundException(MessageCodes.NOT_FOUND, id);
    }
  }

  private EtlExecutionAuditDto toDto(EtlExecutionAuditEntity entity) {
    return mapper.to(entity, EtlExecutionAuditDto.class);
  }

  private static Long requireId(EtlExecutionAuditDto dto) {
    Long id = dto.getId();
    if (id == null) {
      throw new AppException(MessageCodes.ID_REQUIRED);
    }
    return id;
  }

  private static void merge(EtlExecutionAuditEntity target, EtlExecutionAuditDto source) {
    target.setRequestId(source.getRequestId());
    target.setAccountId(source.getAccountId());
    target.setEvent(source.getEvent());
    target.setMarketplace(source.getMarketplace());
    target.setSourceId(source.getSourceId());
    target.setDateFrom(source.getDateFrom());
    target.setDateTo(source.getDateTo());
    target.setStatus(source.getStatus());
    target.setRowsCount(source.getRowsCount());
    target.setErrorMessage(source.getErrorMessage());
  }
}
