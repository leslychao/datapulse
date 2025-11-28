package io.datapulse.etl.event.impl;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseListRaw;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("local")
@EtlSourceMeta(
    event = MarketplaceEvent.WAREHOUSE,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.OZON_WAREHOUSE_LIST
)
public final class TestOzonWarehouseListEventSource implements EventSource {

  private static final URI SNAPSHOT_SOURCE_URI =
      URI.create("https://api-seller.ozon.ru/v1/warehouse/list");

  private static final String SNAPSHOT_RESOURCE_PATH = "ozon_warehouse_list.json";

  private static final String ERROR_MESSAGE =
      "Не удалось подготовить тестовый снапшот складов Ozon из ресурса "
          + SNAPSHOT_RESOURCE_PATH;

  private final MarketplaceProperties marketplaceProperties;

  @Override
  public @NonNull Snapshot<OzonWarehouseListRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    try {
      Path snapshotFile = copyFixtureToSnapshotFile(accountId, event);
      long size = Files.size(snapshotFile);

      return new Snapshot<>(
          OzonWarehouseListRaw.class,
          snapshotFile,
          size,
          SNAPSHOT_SOURCE_URI,
          HttpMethod.POST
      );
    } catch (IOException ex) {
      throw new IllegalStateException(ERROR_MESSAGE, ex);
    }
  }

  private Path copyFixtureToSnapshotFile(long accountId, MarketplaceEvent event)
      throws IOException {

    Resource resource = new ClassPathResource(SNAPSHOT_RESOURCE_PATH);
    if (!resource.exists()) {
      throw new IllegalStateException(
          "Тестовый ресурс не найден в classpath: " + SNAPSHOT_RESOURCE_PATH
      );
    }

    Path baseDir = marketplaceProperties.getStorage().getBaseDir();

    String marketplaceSegment = MarketplaceType.OZON.name().toLowerCase();
    String eventSegment = event.name().toLowerCase();

    Path dir = baseDir
        .resolve(marketplaceSegment)
        .resolve(eventSegment)
        .resolve("test");

    Files.createDirectories(dir);

    String fileName = String.format(
        "snapshot-%s-%s-%d.json",
        marketplaceSegment,
        eventSegment,
        accountId
    );

    Path target = dir.resolve(fileName);

    try (InputStream inputStream = resource.getInputStream()) {
      Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    return target;
  }
}
