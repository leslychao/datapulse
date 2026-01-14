package io.datapulse.core.repository.orderpnl;

import io.datapulse.domain.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.response.orderpnl.OrderPnlResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderPnlReadRepository {

  Page<OrderPnlResponse> searchOrderPnl(
      Long accountId,
      OrderPnlQueryRequest request,
      Pageable pageable
  );
}
