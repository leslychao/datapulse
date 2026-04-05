package io.datapulse.etl.adapter.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.PageCaptureResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class OffsetPagedCaptureTest {

  @Mock private StreamingPageCapture pageCapture;

  @Test
  void stopsAfterRepeatedIdenticalPage() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.SALES_FACT, "src", "req");
    CaptureResult page =
        new CaptureResult(1L, "s3://k", "sha-repeat", 10_000);
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenReturn(new PageCaptureResult(page, null));

    OffsetPagedCapture cut = new OffsetPagedCapture(pageCapture);
    List<CaptureResult> results = cut.captureAllPages(
        context, 100, "unit test",
        (offset, pn) -> Flux.empty());

    assertThat(results).hasSize(1);
  }

  @Test
  void stopsAtMaxPages_evenWhenPagesStayLarge() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.SALES_FACT, "src", "req");
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          int pn = inv.getArgument(2);
          long listOff = inv.getArgument(4, Long.class);
          return new PageCaptureResult(
              new CaptureResult(1L, "k", "p" + pn, 10_000, listOff, null), null);
        });

    OffsetPagedCapture cut = new OffsetPagedCapture(pageCapture);
    List<CaptureResult> results = cut.captureAllPages(
        context, 100, 3,
        OffsetPagedCapture.DEFAULT_SMALL_PAGE_THRESHOLD_BYTES,
        "unit test",
        (offset, pn) -> Flux.empty());

    assertThat(results).hasSize(3);
  }

  @Test
  void passesStartOffsetToCaptureAndFetch() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.SALES_FACT, "src", "req");
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          long listOff = inv.getArgument(4, Long.class);
          return new PageCaptureResult(
              new CaptureResult(1L, "k", "one", 10_000, listOff, null), null);
        });

    OffsetPagedCapture cut = new OffsetPagedCapture(pageCapture);
    cut.captureAllPages(
        context, 100, 5,
        OffsetPagedCapture.DEFAULT_SMALL_PAGE_THRESHOLD_BYTES,
        "unit test", 2500L,
        (offset, pn) -> {
          if (pn == 0) {
            assertThat(offset).isEqualTo(2500L);
          }
          return Flux.empty();
        });

    verify(pageCapture).capture(any(), eq(context), eq(0), any(), eq(2500L), isNull());
  }

  @Test
  void stopsOnSmallPage() {
    CaptureContext context =
        new CaptureContext(1L, 9L, EtlEventType.SALES_FACT, "src", "req");
    when(pageCapture.capture(any(), eq(context), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          int pn = inv.getArgument(2);
          long size = pn == 0 ? 10_000 : 50;
          return new PageCaptureResult(
              new CaptureResult(1L, "k", "p" + pn, size), null);
        });

    OffsetPagedCapture cut = new OffsetPagedCapture(pageCapture);
    List<CaptureResult> results = cut.captureAllPages(
        context, 100, "unit test",
        (offset, pn) -> Flux.empty());

    assertThat(results).hasSize(2);
  }
}
