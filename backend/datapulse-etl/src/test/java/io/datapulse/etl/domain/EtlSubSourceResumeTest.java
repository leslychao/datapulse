package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.Test;

class EtlSubSourceResumeTest {

  @Test
  void nonNegativeLong_parsesPlainAndJsonBranch() {
    IngestContext plain = ctxWithCursor(EtlEventType.SALES_FACT, "42");
    assertThat(EtlSubSourceResume.nonNegativeLong(plain, EtlEventType.SALES_FACT, "X"))
        .isEqualTo(42L);

    String json =
        "{\"o\":{\"OzonFboOrdersReadAdapter\":\"99\",\"OzonFbsOrdersReadAdapter\":\"1\"}}";
    IngestContext mapped = ctxWithCursor(EtlEventType.SALES_FACT, json);
    assertThat(EtlSubSourceResume.nonNegativeLong(
            mapped, EtlEventType.SALES_FACT, "OzonFboOrdersReadAdapter"))
        .isEqualTo(99L);
  }

  @Test
  void lastIdOrEmpty_readsJsonBranch() {
    String json = "{\"o\":{\"OzonStocksReadAdapter\":\"cursor-xyz\"}}";
    IngestContext ctx = ctxWithCursor(EtlEventType.INVENTORY_FACT, json);
    assertThat(EtlSubSourceResume.lastIdOrEmpty(ctx, EtlEventType.INVENTORY_FACT,
        "OzonStocksReadAdapter")).isEqualTo("cursor-xyz");
  }

  @Test
  void ozonFinanceStartPage_defaultsAndClamps() {
    assertThat(EtlSubSourceResume.ozonFinanceStartPage(
        ctxWithCursor(EtlEventType.FACT_FINANCE, null),
        EtlEventType.FACT_FINANCE, "OzonFinanceReadAdapter"))
        .isEqualTo(1);
    assertThat(EtlSubSourceResume.ozonFinanceStartPage(
        ctxWithCursor(EtlEventType.FACT_FINANCE, "0"),
        EtlEventType.FACT_FINANCE, "OzonFinanceReadAdapter"))
        .isEqualTo(1);
    assertThat(EtlSubSourceResume.ozonFinanceStartPage(
        ctxWithCursor(EtlEventType.FACT_FINANCE, "5"),
        EtlEventType.FACT_FINANCE, "OzonFinanceReadAdapter"))
        .isEqualTo(5);
  }

  @Test
  void ozonProductInfoStartBatchIndex_defaults() {
    assertThat(EtlSubSourceResume.ozonProductInfoStartBatchIndex(
        ctxWithCursor(EtlEventType.PRODUCT_DICT, ""),
        EtlEventType.PRODUCT_DICT, "OzonProductInfoReadAdapter"))
        .isZero();
    assertThat(EtlSubSourceResume.ozonProductInfoStartBatchIndex(
        ctxWithCursor(EtlEventType.PRODUCT_DICT, "3"),
        EtlEventType.PRODUCT_DICT, "OzonProductInfoReadAdapter"))
        .isEqualTo(3);
  }

  private static IngestContext ctxWithCursor(EtlEventType event, String lastCursor) {
    if (lastCursor == null) {
      return IngestContextFixtures.any(
          1L, 1L, 1L, MarketplaceType.OZON, Map.of(), "FULL_SYNC",
          EnumSet.allOf(EtlEventType.class), Map.of());
    }
    Map<EtlEventType, IngestContext.CheckpointEntry> checkpoint = Map.of(
        event,
        new IngestContext.CheckpointEntry(EventResultStatus.FAILED, lastCursor, null, null));
    return IngestContextFixtures.any(
        1L, 1L, 1L, MarketplaceType.OZON, Map.of(), "FULL_SYNC",
        EnumSet.allOf(EtlEventType.class), checkpoint);
  }
}
