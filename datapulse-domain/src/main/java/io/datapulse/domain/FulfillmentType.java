package io.datapulse.domain;

/**
 * Тип полнофилмента (исполнения заказа).
 */
public enum FulfillmentType {
  FBO, // Fulfilled by Operator (на складе маркетплейса)
  FBS  // Fulfilled by Seller (со склада продавца)
}
