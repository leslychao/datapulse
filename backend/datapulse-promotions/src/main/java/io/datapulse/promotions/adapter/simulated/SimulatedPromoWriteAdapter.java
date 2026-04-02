package io.datapulse.promotions.adapter.simulated;

import io.datapulse.promotions.adapter.ozon.OzonPromoWriteAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SimulatedPromoWriteAdapter {

  public OzonPromoWriteAdapter.PromoWriteResult simulateActivate(
      long externalActionId,
      List<OzonPromoWriteAdapter.ActivateProductRequest> products) {
    List<Long> ids = products.stream()
        .map(OzonPromoWriteAdapter.ActivateProductRequest::productId)
        .toList();

    log.info("SIMULATED promo activate: actionId={}, productIds={}",
        externalActionId, ids);
    return new OzonPromoWriteAdapter.PromoWriteResult(
        ids, List.of(), "{\"simulated\":true}");
  }

  public OzonPromoWriteAdapter.PromoWriteResult simulateDeactivate(
      long externalActionId,
      List<Long> productIds) {
    log.info("SIMULATED promo deactivate: actionId={}, productIds={}",
        externalActionId, productIds);
    return new OzonPromoWriteAdapter.PromoWriteResult(
        productIds, List.of(), "{\"simulated\":true}");
  }
}
