package io.datapulse.execution.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexUpdatePricesResponse(
    String status
) {

  public boolean isOk() {
    return "OK".equalsIgnoreCase(status);
  }
}
