package io.datapulse.etl.adapter.yandex;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.config.EtlProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Async report capture pipeline: generate → poll → download → parse.
 * <p>
 * Yandex Market finance data is ONLY available through async reports (no synchronous API).
 * This utility encapsulates the full lifecycle:
 * <ol>
 *   <li>POST generate path → extract {@code reportId}</li>
 *   <li>Poll {@code GET /v2/reports/info/{reportId}} until DONE or FAILED</li>
 *   <li>Download report file (external URL, no Api-Key)</li>
 *   <li>Parse JSON → {@code List<T>}</li>
 * </ol>
 * Not a Spring bean — instantiated manually by adapters that need async reports.
 */
@Slf4j
@RequiredArgsConstructor
public class AsyncReportCapture {

  private static final String REPORT_STATUS_PATH = "/v2/reports/info/%s";
  private static final Duration INITIAL_WAIT = Duration.ofSeconds(5);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
  private static final int MAX_POLL_ATTEMPTS = 60;
  private static final int DOWNLOAD_RETRIES = 1;

  private final YandexApiCaller apiCaller;
  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;
  private final EtlProperties etlProperties;

  /**
   * Full async report lifecycle: generate → poll → download → parse.
   *
   * @param generatePath  API path for report generation (e.g. {@code /v2/reports/.../generate})
   * @param requestBody   request body for generation
   * @param apiKey        Yandex Api-Key
   * @param connectionId  connection ID for rate limiting
   * @param rowType       target DTO class for deserialization
   * @return parsed report rows
   */
  public <T> List<T> captureReport(
      String generatePath,
      Object requestBody,
      String apiKey,
      long connectionId,
      Class<T> rowType) {

    String reportId = generateReport(generatePath, requestBody, apiKey, connectionId);
    log.info("Report generation requested: reportId={}, connectionId={}", reportId, connectionId);

    String downloadUrl = pollUntilReady(reportId, apiKey, connectionId);
    log.info("Report ready: reportId={}, connectionId={}", reportId, connectionId);

    Path tempFile = downloadReport(downloadUrl, reportId);
    try {
      List<T> rows = parseReport(tempFile, rowType, reportId);
      log.info("Report parsed: reportId={}, rows={}, connectionId={}",
          reportId, rows.size(), connectionId);
      return rows;
    } finally {
      deleteSilently(tempFile);
    }
  }

  private String generateReport(
      String generatePath, Object requestBody, String apiKey, long connectionId) {
    byte[] responseBytes = collectBytes(
        apiCaller.post(generatePath, connectionId, RateLimitGroup.YANDEX_REPORTS,
            apiKey, requestBody));

    try {
      JsonNode root = objectMapper.readTree(responseBytes);
      JsonNode resultNode = root.path("result");
      String reportId = resultNode.path("reportId").asText(null);

      if (reportId == null || reportId.isBlank()) {
        String preview = truncate(new String(responseBytes), 500);
        throw new IllegalStateException(
            "Report generation did not return reportId: response=%s".formatted(preview));
      }
      return reportId;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse report generation response", e);
    }
  }

  private String pollUntilReady(String reportId, String apiKey, long connectionId) {
    sleep(INITIAL_WAIT);

    for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
      String statusPath = REPORT_STATUS_PATH.formatted(reportId);
      byte[] responseBytes = collectBytes(
          apiCaller.get(statusPath, connectionId, RateLimitGroup.YANDEX_REPORTS, apiKey));

      try {
        JsonNode root = objectMapper.readTree(responseBytes);
        JsonNode resultNode = root.path("result");
        String status = resultNode.path("status").asText("UNKNOWN");

        switch (status) {
          case "DONE" -> {
            String fileUrl = resultNode.path("file").asText(null);
            if (fileUrl == null || fileUrl.isBlank()) {
              throw new IllegalStateException(
                  "Report DONE but no download URL: reportId=%s".formatted(reportId));
            }
            return fileUrl;
          }
          case "FAILED" ->
              throw new IllegalStateException(
                  "Report generation failed: reportId=%s".formatted(reportId));
          case "PENDING", "GENERATING" -> {
            log.debug("Report still generating: reportId={}, status={}, attempt={}/{}",
                reportId, status, attempt + 1, MAX_POLL_ATTEMPTS);
            sleep(POLL_INTERVAL);
          }
          default -> {
            log.warn("Unknown report status: reportId={}, status={}", reportId, status);
            sleep(POLL_INTERVAL);
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException(
            "Failed to parse report status response: reportId=%s".formatted(reportId), e);
      }
    }

    throw new IllegalStateException(
        "Report poll timeout after %d attempts: reportId=%s"
            .formatted(MAX_POLL_ATTEMPTS, reportId));
  }

  private Path downloadReport(String downloadUrl, String reportId) {
    int retries = 0;
    while (true) {
      try {
        return doDownload(downloadUrl, reportId);
      } catch (Exception e) {
        if (retries < DOWNLOAD_RETRIES) {
          retries++;
          log.warn("Report download failed, retrying ({}/{}): reportId={}, url={}",
              retries, DOWNLOAD_RETRIES, reportId, downloadUrl, e);
          sleep(Duration.ofSeconds(2));
        } else {
          throw new IllegalStateException(
              "Report download failed after %d retries: reportId=%s"
                  .formatted(DOWNLOAD_RETRIES, reportId), e);
        }
      }
    }
  }

  private Path doDownload(String downloadUrl, String reportId) {
    Path tempFile = createTempFile(reportId);

    Flux<DataBuffer> body = webClientBuilder.build()
        .get()
        .uri(downloadUrl)
        .retrieve()
        .bodyToFlux(DataBuffer.class);

    try (OutputStream out = Files.newOutputStream(tempFile)) {
      body.doOnNext(buffer -> {
            try {
              byte[] bytes = new byte[buffer.readableByteCount()];
              buffer.read(bytes);
              out.write(bytes);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            } finally {
              DataBufferUtils.release(buffer);
            }
          })
          .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
          .blockLast();
    } catch (IOException e) {
      deleteSilently(tempFile);
      throw new UncheckedIOException(
          "Failed to download report: reportId=%s".formatted(reportId), e);
    } catch (Exception e) {
      deleteSilently(tempFile);
      throw new IllegalStateException(
          "Failed to download report: reportId=%s".formatted(reportId), e);
    }

    try {
      long size = Files.size(tempFile);
      log.debug("Report downloaded: reportId={}, bytes={}", reportId, size);
      return tempFile;
    } catch (IOException e) {
      deleteSilently(tempFile);
      throw new UncheckedIOException("Failed to stat downloaded report file", e);
    }
  }

  private <T> List<T> parseReport(Path tempFile, Class<T> rowType, String reportId) {
    try {
      byte[] content = Files.readAllBytes(tempFile);
      if (content.length == 0) {
        log.warn("Empty report file: reportId={}", reportId);
        return List.of();
      }
      return objectMapper.readValue(
          content,
          objectMapper.getTypeFactory().constructCollectionType(List.class, rowType));
    } catch (IOException e) {
      logParseError(tempFile, reportId);
      throw new IllegalStateException(
          "Failed to parse report: reportId=%s".formatted(reportId), e);
    }
  }

  private void logParseError(Path tempFile, String reportId) {
    try {
      String raw = Files.readString(tempFile);
      String preview = truncate(raw, 500);
      log.error("Report parse failure: reportId={}, preview={}", reportId, preview);
    } catch (IOException ignored) {
      log.error("Report parse failure: reportId={}, could not read file", reportId);
    }
  }

  private byte[] collectBytes(Flux<DataBuffer> flux) {
    return Mono.from(
            DataBufferUtils.join(flux)
                .map(buffer -> {
                  try {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                  } finally {
                    DataBufferUtils.release(buffer);
                  }
                }))
        .block();
  }

  private Path createTempFile(String reportId) {
    try {
      Path tempDir = Path.of(etlProperties.tempDir());
      Files.createDirectories(tempDir);
      return Files.createTempFile(tempDir, "report-" + reportId + "-", ".json");
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create temp file for report", e);
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Report polling interrupted", e);
    }
  }

  private static String truncate(String str, int maxLength) {
    if (str == null) {
      return "<null>";
    }
    return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
  }

  private static void deleteSilently(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Failed to delete temp file: path={}", file, e);
    }
  }
}
