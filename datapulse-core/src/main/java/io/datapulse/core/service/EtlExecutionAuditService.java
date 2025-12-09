package io.datapulse.core.service;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.EtlExecutionAuditRepository;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EtlExecutionAuditService
    extends AbstractCrudService<EtlExecutionAuditDto, EtlExecutionAuditEntity> {

  private final EtlExecutionAuditRepository repository;
  private final MapperFacade mapperFacade;

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected EtlExecutionAuditRepository repository() {
    return repository;
  }

  @Override
  protected Class<EtlExecutionAuditDto> dtoType() {
    return EtlExecutionAuditDto.class;
  }

  @Override
  protected Class<EtlExecutionAuditEntity> entityType() {
    return EtlExecutionAuditEntity.class;
  }

  @Override
  protected void validateOnCreate(EtlExecutionAuditDto dto) {
  }

  @Override
  protected void validateOnUpdate(Long id, EtlExecutionAuditDto dto,
      EtlExecutionAuditEntity existing) {
  }

  @Override
  protected EtlExecutionAuditEntity merge(EtlExecutionAuditEntity target,
      EtlExecutionAuditDto source) {
    target.setRequestId(source.getRequestId());
    target.setAccountId(source.getAccountId());
    target.setEvent(source.getEvent());
    target.setMarketplace(source.getMarketplace());
    target.setSourceId(source.getSourceId());
    target.setDateFrom(source.getDateFrom());
    target.setDateTo(source.getDateTo());
    target.setStatus(source.getStatus());
    target.setRowsCount(source.getRowsCount());
    target.setRetryCount(source.getRetryCount());
    target.setAttemptsTotal(source.getAttemptsTotal());
    target.setErrorMessage(source.getErrorMessage());
    return target;
  }
}
