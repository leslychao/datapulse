package io.datapulse.marketplaces.service;

import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.config.MarketplaceProperties.Cleanup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupService {

  private final MarketplaceProperties properties;

  @Scheduled(
      fixedDelayString   = "#{@marketplaceProperties.storage.cleanup.interval.toMillis()}",
      initialDelayString = "#{@marketplaceProperties.storage.cleanup.interval.toMillis()}"
  )
  public void removeExpiredFiles() {
    MarketplaceProperties.Storage storage = properties.getStorage();
    if (storage == null || storage.getBaseDir() == null) {
      log.warn("Skipping snapshot cleanup: storage base dir is not configured");
      return;
    }

    Path baseDir = storage.getBaseDir();
    if (!Files.exists(baseDir)) {
      log.debug("Skipping snapshot cleanup: base dir does not exist: {}", baseDir);
      return;
    }

    Cleanup cleanup = storage.getCleanup();
    Duration maxAge = cleanup != null
        ? cleanup.getMaxAge()
        : Duration.ofHours(6);

    Instant threshold = Instant.now().minus(maxAge);

    try (Stream<Path> paths = Files.walk(baseDir)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> isOlderThan(path, threshold))
          .forEach(this::deleteQuietly);
    } catch (IOException e) {
      log.warn("Failed to clean up snapshot directory: {}", baseDir, e);
    }
  }

  private boolean isOlderThan(Path path, Instant threshold) {
    try {
      return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
    } catch (IOException e) {
      log.debug("Could not read last modified time for file: {}", path, e);
      return false;
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
      log.info("Deleted expired snapshot file: {}", path);
    } catch (IOException e) {
      log.warn("Failed to delete expired snapshot file: {}", path, e);
    }
  }
}
