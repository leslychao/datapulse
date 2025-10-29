package io.datapulse.marketplaces.event.transform;

import io.datapulse.domain.dto.SaleDto;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import io.datapulse.marketplaces.mapper.wb.WbSaleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbSalesTransformer implements EventItemTransformer<WbSaleRaw, SaleDto> {

  private final WbSaleMapper mapper;

  @Override
  public Class<WbSaleRaw> rawType() {
    return WbSaleRaw.class;
  }

  @Override
  public SaleDto transform(WbSaleRaw raw) {
    return mapper.toDto(raw);
  }
}
