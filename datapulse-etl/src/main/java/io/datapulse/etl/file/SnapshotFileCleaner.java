package io.datapulse.etl.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class SnapshotFileCleaner {

  public void deleteSafely(Path file, String reason) {
    if (file == null) {
      log.warn("SnapshotFileCleaner.deleteSafely(): null file; reason={}", reason);
      return;
    }
    try {
      boolean deleted = Files.deleteIfExists(file);
      log.debug(
          "Snapshot file delete: file={}, deleted={}, reason={}",
          file,
          deleted,
          reason
      );
    } catch (IOException ex) {
      String root = ExceptionUtils.getRootCauseMessage(ex);
      log.warn(
          "Snapshot file delete failed: file={}, reason={}, rootCause={}",
          file,
          reason,
          root
      );
    }
  }
}
