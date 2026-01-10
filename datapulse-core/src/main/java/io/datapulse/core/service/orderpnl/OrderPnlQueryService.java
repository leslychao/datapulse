package io.datapulse.core.service.orderpnl;

import io.datapulse.core.repository.orderpnl.OrderPnlReadRepository;
import io.datapulse.domain.dto.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.dto.response.orderpnl.OrderPnlResponse;
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
      @Valid @NotNull OrderPnlQueryRequest request,
      @NotNull Pageable pageable
  ) {
    return repository.find(request, pageable);
  }
}
