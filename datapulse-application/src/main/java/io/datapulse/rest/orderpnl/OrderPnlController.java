package io.datapulse.rest.orderpnl;

import io.datapulse.core.service.orderpnl.OrderPnlQueryService;
import io.datapulse.domain.dto.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.dto.response.orderpnl.OrderPnlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/order-pnl",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class OrderPnlController {

  private final OrderPnlQueryService service;

  @GetMapping
  public Page<OrderPnlResponse> list(
      @Valid OrderPnlQueryRequest request,
      Pageable pageable
  ) {
    return service.find(request, pageable);
  }
}
