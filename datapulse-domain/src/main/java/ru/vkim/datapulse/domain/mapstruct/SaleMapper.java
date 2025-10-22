package ru.vkim.datapulse.domain.mapstruct;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.vkim.datapulse.domain.marketplace.SaleEvent;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SaleMapper {
    // TODO: добавить мапперы DTO WB/Ozon -> SaleEvent
}
