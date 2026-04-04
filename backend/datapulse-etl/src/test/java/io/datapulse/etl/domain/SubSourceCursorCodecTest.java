package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.Test;

class SubSourceCursorCodecTest {

  @Test
  void resolve_returnsPlain_when_notJson() {
    assertThat(SubSourceCursorCodec.resolve("  4200  ", "AnySource")).isEqualTo("4200");
  }

  @Test
  void resolve_returnsSourceBranch_when_jsonMap() {
    String raw = "{\"o\":{\"OzonFboOrdersReadAdapter\":\"100\",\"OzonFbsOrdersReadAdapter\":\"200\"}}";
    assertThat(SubSourceCursorCodec.resolve(raw, "OzonFboOrdersReadAdapter")).isEqualTo("100");
    assertThat(SubSourceCursorCodec.resolve(raw, "OzonFbsOrdersReadAdapter")).isEqualTo("200");
    assertThat(SubSourceCursorCodec.resolve(raw, "Missing")).isNull();
  }

  @Test
  void merge_returnsNull_when_noCursors() {
    assertThat(SubSourceCursorCodec.mergeSubSourceLastCursors(
        List.of(SubSourceResult.failed("a", "e")))).isNull();
  }

  @Test
  void merge_returnsPlain_when_singleCursor() {
    assertThat(SubSourceCursorCodec.mergeSubSourceLastCursors(
        List.of(SubSourceResult.failed("a", "e", "99")))).isEqualTo("99");
  }

  @Test
  void merge_returnsJson_when_multipleCursors() {
    String merged = SubSourceCursorCodec.mergeSubSourceLastCursors(List.of(
        SubSourceResult.failed("s1", "e1", "10"),
        SubSourceResult.failed("s2", "e2", "20")));
    assertThat(merged).contains("\"s1\":\"10\"").contains("\"s2\":\"20\"");

    IngestContext ctx = IngestContextFixtures.any(
        1L, 1L, 1L, MarketplaceType.OZON,
        Map.of(), "FULL_SYNC",
        EnumSet.allOf(EtlEventType.class),
        Map.of(
            EtlEventType.SALES_FACT,
            new IngestContext.CheckpointEntry(
                EventResultStatus.FAILED, merged, null, null)));
    assertThat(ctx.resumeSubSourceCursor(EtlEventType.SALES_FACT, "s1")).isEqualTo("10");
    assertThat(ctx.resumeSubSourceCursor(EtlEventType.SALES_FACT, "s2")).isEqualTo("20");
  }

  @Test
  void eventResult_failed_usesMerge() {
    EventResult r = EventResult.failed(EtlEventType.SALES_FACT, List.of(
        SubSourceResult.failed("A", "e", "1"),
        SubSourceResult.failed("B", "e", "2")));
    assertThat(r.lastCursor()).contains("A").contains("B");
  }
}
