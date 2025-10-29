package io.datapulse.marketplaces.event;

/**
 * Бизнес-уровень: единые события для WB/Ozon.
 */
public enum BusinessEvent {
  ORDER_POSTING,      // заказы/отправки/статусы
  RETURN,             // возвраты/невыкупы/штрафы
  SALES_FACT,         // аналитика продаж / факт продаж
  STOCK_LEVEL,        // остатки
  PRICE_SNAPSHOT,     // цены/скидки
  REVIEW,             // отзывы/вопросы
  AD_PERFORMANCE,     // реклама (статы)
  CATALOG_ITEM        // карточки/ассортимент
}
