package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Consumer;

import io.datapulse.etl.persistence.JobItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubSourceRunnerTest {

  @Mock private RawPageReader rawPageReader;
  @Mock private JobItemRepository jobItemRepository;
  @InjectMocks private SubSourceRunner runner;

  @Nested
  @DisplayName("processPages()")
  class ProcessPages {

    @Test
    void should_returnSuccess_when_allPagesProcessed() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(1L, "s3://bucket/key1", "sha256", 1024L),
          new CaptureResult(2L, "s3://bucket/key2", "sha256", 2048L));

      stubReadBatched("s3://bucket/key1", List.of("r1", "r2"));
      stubReadBatched("s3://bucket/key2", List.of("r3"));

      SubSourceResult result = runner.processPages(
          "TestSource", pages, String.class, batch -> {});

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED);
      assertThat(result.recordsProcessed()).isEqualTo(3);
    }

    @Test
    void should_returnFailed_when_allPagesFail() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(1L, "s3://bucket/key1", "sha256", 1024L));

      doThrow(new RuntimeException("S3 error"))
          .when(rawPageReader).readBatched(eq("s3://bucket/key1"), eq(String.class), any());

      SubSourceResult result = runner.processPages(
          "TestSource", pages, String.class, batch -> {});

      assertThat(result.status()).isEqualTo(EventResultStatus.FAILED);
    }

    @Test
    void should_attachResumeToken_when_pageFails_and_listRequestOffsetPresent() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(1L, "s3://bucket/key1", "sha256", 1024L, 5000L, null));

      doThrow(new RuntimeException("S3 error"))
          .when(rawPageReader).readBatched(eq("s3://bucket/key1"), eq(String.class), any());

      SubSourceResult result = runner.processPages(
          "TestSource", pages, String.class, batch -> {});

      assertThat(result.status()).isEqualTo(EventResultStatus.FAILED);
      assertThat(result.lastCursor()).isEqualTo("5000");
    }

    @Test
    void should_attachResumeToken_when_pageFails_and_listResumeKeyPresent() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(1L, "s3://bucket/key1", "sha256", 1024L, null, "abc-token"));

      doThrow(new RuntimeException("S3 error"))
          .when(rawPageReader).readBatched(eq("s3://bucket/key1"), eq(String.class), any());

      SubSourceResult result = runner.processPages(
          "TestSource", pages, String.class, batch -> {});

      assertThat(result.status()).isEqualTo(EventResultStatus.FAILED);
      assertThat(result.lastCursor()).isEqualTo("abc-token");
    }

    @Test
    void should_returnPartial_when_somePagesFail() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(1L, "s3://bucket/key1", "sha256", 1024L),
          new CaptureResult(2L, "s3://bucket/key2", "sha256", 2048L));

      stubReadBatched("s3://bucket/key1", List.of("r1", "r2"));
      doThrow(new RuntimeException("S3 error"))
          .when(rawPageReader).readBatched(eq("s3://bucket/key2"), eq(String.class), any());

      SubSourceResult result = runner.processPages(
          "TestSource", pages, String.class, batch -> {});

      assertThat(result.status()).isEqualTo(EventResultStatus.COMPLETED_WITH_ERRORS);
      assertThat(result.recordsProcessed()).isEqualTo(2);
    }

    @Test
    void should_markJobItemStatus_when_pageProcessed() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(10L, "s3://bucket/key1", "sha256", 1024L));

      stubReadBatched("s3://bucket/key1", List.of("r1"));

      runner.processPages("TestSource", pages, String.class, batch -> {});

      verify(jobItemRepository).updateStatus(10L, JobItemStatus.PROCESSED);
    }

    @Test
    void should_markJobItemFailed_when_pageProcessingFails() {
      List<CaptureResult> pages = List.of(
          new CaptureResult(10L, "s3://bucket/key1", "sha256", 1024L));

      doThrow(new RuntimeException("Read error"))
          .when(rawPageReader).readBatched(eq("s3://bucket/key1"), eq(String.class), any());

      runner.processPages("TestSource", pages, String.class, batch -> {});

      verify(jobItemRepository).updateStatus(10L, JobItemStatus.FAILED);
    }
  }

  @SuppressWarnings("unchecked") // safe: test helper casting generic Consumer
  private void stubReadBatched(String s3Key, List<String> records) {
    doAnswer(inv -> {
      Consumer<List<String>> consumer = inv.getArgument(2);
      consumer.accept(records);
      return null;
    }).when(rawPageReader).readBatched(eq(s3Key), eq(String.class), any());
  }
}
