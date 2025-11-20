package io.datapulse.core.service;

import io.datapulse.core.service.FileStreamingService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

public final class TestSnapshotUtils {

  private TestSnapshotUtils() {
  }

  public static Path copyOzonSnapshotFromResource(
      FileStreamingService fileStreamingService,
      DataBufferFactory dataBufferFactory,
      String resourcePath,
      Path targetFile
  ) {
    try {
      Resource resource = new ClassPathResource(resourcePath);

      Flux<DataBuffer> source =
          DataBufferUtils.read(resource.getFile().toPath(), dataBufferFactory, 8192);

      // используем боевой FileStreamingService
      return fileStreamingService.writeToPermanentFile(source, targetFile);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to copy test snapshot from resource: " + resourcePath,
          e
      );
    }
  }
}
