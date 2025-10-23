package io.datapulse.app.web;

import io.datapulse.etl.gateway.SalesEtlGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl")
@RequiredArgsConstructor
public class EtlController {

  private final SalesEtlGateway salesEtlGateway;

  @PostMapping("/run/sales")
  public ResponseEntity<String> runSales() {
    salesEtlGateway.triggerSalesSync();
    return ResponseEntity.ok("Запущена синхронизация продаж");
  }
}
