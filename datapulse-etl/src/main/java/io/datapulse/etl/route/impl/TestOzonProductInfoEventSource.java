package io.datapulse.etl.route.impl;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.domain.marketplace.Snapshot;
import io.datapulse.etl.route.EtlSourceMeta;
import io.datapulse.etl.route.EventSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import lombok.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@EtlSourceMeta(
    event = MarketplaceEvent.SALES_FACT,
    marketplace = MarketplaceType.OZON,
    order = 1
)
public final class TestOzonProductInfoEventSource implements EventSource {

  private static final URI DUMMY_URI = URI.create("test://ozon/product-info");
  private static final String SNAPSHOT_RESOURCE_PATH = "ozon-product-info.json";
  private static final String TEMP_FILE_PREFIX = "ozon-product-info-";
  private static final String TEMP_FILE_SUFFIX = ".json";
  private static final String ERROR_MESSAGE =
      "Не удалось подготовить тестовый снапшот Ozon Product Info из ресурса "
          + SNAPSHOT_RESOURCE_PATH;

  @Override
  public @NonNull Snapshot<OzonProductInfoRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    try {
      Path snapshotFile = copyFixtureToTempFile();
      long size = Files.size(snapshotFile);

      return new Snapshot<>(
          OzonProductInfoRaw.class,
          snapshotFile,
          size,
          DUMMY_URI,
          HttpMethod.POST
      );
    } catch (IOException e) {
      throw new IllegalStateException(ERROR_MESSAGE, e);
    }
  }

  private Path copyFixtureToTempFile() throws IOException {
    Resource resource = new ClassPathResource(SNAPSHOT_RESOURCE_PATH);
    Path tempFile = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);

    try (InputStream in = resource.getInputStream()) {
      Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
    }

    return tempFile;
  }
}
