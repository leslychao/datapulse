package io.datapulse.core.service.orderpnl;

import io.datapulse.core.repository.orderpnl.OrderPnlReadRepository;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.response.orderpnl.OrderPnlResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class OrderPnlQueryService {

  private final OrderPnlReadRepository repository;

  public Page<OrderPnlResponse> find(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) OrderPnlQueryRequest request,
      @NotNull(message = ValidationKeys.PAGEABLE_REQUIRED) Pageable pageable
  ) {
    return repository.find(accountId, request, pageable);
  }
}
