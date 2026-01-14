package io.datapulse.rest.orderpnl;

import io.datapulse.core.service.orderpnl.OrderPnlQueryService;
import io.datapulse.domain.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.response.orderpnl.OrderPnlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/accounts/{accountId}/order-pnl",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class OrderPnlController {

  private final OrderPnlQueryService service;

  @GetMapping
  @PreAuthorize("@accountAccessService.canRead(#accountId)")
  public Page<OrderPnlResponse> list(
      @PathVariable("accountId")
      Long accountId,
      OrderPnlQueryRequest request,
      Pageable pageable
  ) {
    return service.searchOrderPnl(accountId, request, pageable);
  }
}
