package io.datapulse.etl.event.impl;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.SALES_FACT,
    marketplace = MarketplaceType.OZON,
    order = 1
)
public final class TestOzonProductInfoEventSource implements EventSource {

  private static final URI SNAPSHOT_SOURCE_URI =
      URI.create("https://api-seller.ozon.ru/v3/product/info/list");

  private static final String SNAPSHOT_RESOURCE_PATH = "ozon-product-info.json";

  private final MarketplaceProperties marketplaceProperties;

  @Override
  public @NonNull Snapshot<OzonProductInfoRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    try {
      Path snapshotFile = copyFixtureToSnapshotFile(accountId, event, from, to);
      long size = Files.size(snapshotFile);

      return new Snapshot<>(
          OzonProductInfoRaw.class,
          snapshotFile,
          size,
          SNAPSHOT_SOURCE_URI,
          HttpMethod.POST
      );
    } catch (IOException ex) {
      String msg = "Failed to prepare Ozon Product Info test snapshot: " + ExceptionUtils.getRootCauseMessage(ex);
      throw new IllegalStateException(msg, ex);
    }
  }

  private Path copyFixtureToSnapshotFile(
      long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to
  ) throws IOException {

    Resource resource = new ClassPathResource(SNAPSHOT_RESOURCE_PATH);
    if (!resource.exists()) {
      throw new IllegalStateException("Test resource not found: " + SNAPSHOT_RESOURCE_PATH);
    }

    Path baseDir = marketplaceProperties.getStorage().getBaseDir();

    String marketplaceSegment = MarketplaceType.OZON.name().toLowerCase();
    String eventSegment = event.name().toLowerCase();

    Path dir = baseDir
        .resolve(marketplaceSegment)
        .resolve(eventSegment)
        .resolve("test-product-info");

    Files.createDirectories(dir);

    String fileName = String.format(
        "snapshot-%s-%s-product-info-%d-%s-%s.json",
        marketplaceSegment,
        eventSegment,
        accountId,
        from,
        to
    );

    Path target = dir.resolve(fileName);

    try (InputStream inputStream = resource.getInputStream()) {
      Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    return target;
  }
}
