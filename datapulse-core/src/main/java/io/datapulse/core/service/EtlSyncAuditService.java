package io.datapulse.core.service;

import io.datapulse.core.entity.EtlSyncAuditEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.EtlSyncAuditRepository;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EtlSyncAuditService
    extends AbstractCrudService<EtlSyncAuditDto, EtlSyncAuditEntity> {

  private final EtlSyncAuditRepository repository;
  private final MapperFacade mapperFacade;

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected EtlSyncAuditRepository repository() {
    return repository;
  }

  @Override
  protected Class<EtlSyncAuditDto> dtoType() {
    return EtlSyncAuditDto.class;
  }

  @Override
  protected Class<EtlSyncAuditEntity> entityType() {
    return EtlSyncAuditEntity.class;
  }

  @Override
  protected void validateOnCreate(EtlSyncAuditDto dto) {
  }

  @Override
  protected void validateOnUpdate(Long id, EtlSyncAuditDto dto, EtlSyncAuditEntity existing) {
  }

  @Override
  protected EtlSyncAuditEntity merge(EtlSyncAuditEntity target, EtlSyncAuditDto source) {
    target.setRequestId(source.getRequestId());
    target.setAccountId(source.getAccountId());
    target.setEvent(source.getEvent());
    target.setDateFrom(source.getDateFrom());
    target.setDateTo(source.getDateTo());
    return target;
  }
}
