package io.datapulse.core.repository.orderpnl;

import io.datapulse.domain.dto.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.dto.response.orderpnl.OrderPnlResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderPnlReadRepository {

  Page<OrderPnlResponse> find(OrderPnlQueryRequest request, Pageable pageable);
}
