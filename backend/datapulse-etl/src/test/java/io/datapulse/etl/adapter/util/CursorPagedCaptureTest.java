package io.datapulse.etl.adapter.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import io.datapulse.etl.adapter.util.CursorPagedCapture.CursorPageSpec;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class CursorPagedCaptureTest {

  @Mock private StreamingPageCapture pageCapture;

  @Test
  void stopsWhenCursorIsNull() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.PRODUCT_DICT, "src", "req");
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenReturn(new PageCaptureResult(
            new CaptureResult(1L, "k", "sha1", 5_000), null));

    CursorPagedCapture cut = new CursorPagedCapture(pageCapture);
    CursorPageSpec spec = new CursorPageSpec(
        NoCursorExtractor.INSTANCE, "test",
        (cursor, pn) -> Flux.empty());

    List<CaptureResult> results = cut.captureAllPages(
        context, null, spec, CursorPagedCapture.NO_OP);

    assertThat(results).hasSize(1);
  }

  @Test
  void stopsWhenCursorDoesNotAdvance() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.PRODUCT_DICT, "src", "req");
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenReturn(new PageCaptureResult(
            new CaptureResult(1L, "k", "sha1", 5_000), "same-cursor"));

    CursorPagedCapture cut = new CursorPagedCapture(pageCapture);
    CursorPageSpec spec = new CursorPageSpec(
        NoCursorExtractor.INSTANCE, "test",
        (cursor, pn) -> Flux.empty());

    List<CaptureResult> results = cut.captureAllPages(
        context, "same-cursor", spec, CursorPagedCapture.NO_OP);

    assertThat(results).hasSize(1);
  }

  @Test
  void paginatesThroughMultiplePages() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.PRODUCT_DICT, "src", "req");
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          int pn = inv.getArgument(2);
          String nextCursor = pn < 2 ? "cursor-" + (pn + 1) : "";
          return new PageCaptureResult(
              new CaptureResult(1L, "k", "sha" + pn, 5_000), nextCursor);
        });

    CursorPagedCapture cut = new CursorPagedCapture(pageCapture);
    CursorPageSpec spec = new CursorPageSpec(
        NoCursorExtractor.INSTANCE, "test",
        (cursor, pn) -> Flux.empty());

    List<CaptureResult> results = cut.captureAllPages(
        context, "", spec, CursorPagedCapture.NO_OP);

    assertThat(results).hasSize(3);
  }

  @Test
  void evaluateCursor_stopsOnNull() {
    var outcome = CursorPagedCapture.evaluateCursor(null, "prev");
    assertThat(outcome.stop()).isTrue();
    assertThat(outcome.nonAdvancingCursor()).isFalse();
  }

  @Test
  void evaluateCursor_stopsOnEmpty() {
    var outcome = CursorPagedCapture.evaluateCursor("", "prev");
    assertThat(outcome.stop()).isTrue();
    assertThat(outcome.nonAdvancingCursor()).isFalse();
  }

  @Test
  void evaluateCursor_stopsOnNonAdvancing() {
    var outcome = CursorPagedCapture.evaluateCursor("same", "same");
    assertThat(outcome.stop()).isTrue();
    assertThat(outcome.nonAdvancingCursor()).isTrue();
  }

  @Test
  void evaluateCursor_continuesOnAdvancing() {
    var outcome = CursorPagedCapture.evaluateCursor("next", "prev");
    assertThat(outcome.stop()).isFalse();
    assertThat(outcome.nonAdvancingCursor()).isFalse();
  }
}
