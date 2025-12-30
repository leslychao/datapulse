package io.datapulse.core.service;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.EtlExecutionAuditRepository;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
  protected void validateOnUpdate(
      Long id,
      EtlExecutionAuditDto dto,
      EtlExecutionAuditEntity existing
  ) {
  }

  @Override
  protected EtlExecutionAuditEntity beforeSave(EtlExecutionAuditEntity entity) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    entity.setCreatedAt(now);
    return entity;
  }

  @Override
  protected EtlExecutionAuditEntity beforeUpdate(EtlExecutionAuditEntity entity) {
    return entity;
  }

  @Override
  protected EtlExecutionAuditEntity merge(
      EtlExecutionAuditEntity target,
      EtlExecutionAuditDto source
  ) {
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
    return target;
  }

  public Optional<EtlExecutionAuditDto> findLatestSync(
      long accountId,
      MarketplaceType marketplace,
      String sourceId,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    return repository
        .findTopByAccountIdAndMarketplaceAndSourceIdAndDateFromAndDateToOrderByCreatedAtDesc(
            accountId,
            marketplace,
            sourceId,
            dateFrom,
            dateTo
        )
        .map(entity -> mapperFacade.to(entity, EtlExecutionAuditDto.class));
  }

}
