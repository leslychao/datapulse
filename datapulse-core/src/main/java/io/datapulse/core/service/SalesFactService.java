package io.datapulse.core.service;

import io.datapulse.core.entity.SalesFactEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.SalesFactRepository;
import io.datapulse.domain.dto.SalesFactDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesFactService extends AbstractCrudService<SalesFactDto, SalesFactEntity> {

  private final SalesFactRepository repository;
  private final MapperFacade mapperFacade;

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected SalesFactRepository repository() {
    return repository;
  }

  @Override
  protected Class<SalesFactDto> dtoType() {
    return SalesFactDto.class;
  }

  @Override
  protected Class<SalesFactEntity> entityType() {
    return SalesFactEntity.class;
  }

  @Override
  protected void validateOnCreate(SalesFactDto dto) {
  }

  @Override
  protected void validateOnUpdate(Long id, SalesFactDto dto, SalesFactEntity existing) {
  }

  @Override
  protected SalesFactEntity merge(SalesFactEntity target, SalesFactDto source) {
    target.setAccountId(source.getAccountId());
    target.setMarketplace(source.getMarketplace());

    target.setOperationDate(source.getOperationDate());
    target.setOperationDateTime(source.getOperationDateTime());

    target.setOfferId(source.getOfferId());
    target.setBarcode(source.getBarcode());
    target.setSize(source.getSize());
    target.setWarehouseName(source.getWarehouseName());
    target.setRegionName(source.getRegionName());

    target.setQuantity(source.getQuantity());

    target.setGrossAmount(source.getGrossAmount());
    target.setCommissionAmount(source.getCommissionAmount());
    target.setLogisticsAndFeesAmount(source.getLogisticsAndFeesAmount());
    target.setPromoAmount(source.getPromoAmount());
    target.setNetAmount(source.getNetAmount());

    target.setOperationType(source.getOperationType());
    target.setExternalOperationId(source.getExternalOperationId());
    target.setCurrencyCode(source.getCurrencyCode());

    return target;
  }
}
