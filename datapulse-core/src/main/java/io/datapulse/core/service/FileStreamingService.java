package io.datapulse.core.service;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class FileStreamingService {

  public Path writeToPermanentFile(Flux<DataBuffer> source, Path targetFile) {
    ensureParentDirectory(targetFile);
    Path tmp = createSiblingTempFile(targetFile);

    try {
      DataBufferUtils.write(
          source,
          tmp,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
      ).block();

      moveAtomicallyOrReplace(tmp, targetFile);
      return targetFile;
    } catch (Exception e) {
      safeDelete(tmp);
      throw new AppException(MessageCodes.DOWNLOAD_FAILED, targetFile);
    }
  }

  private static void ensureParentDirectory(Path target) {
    try {
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new AppException(MessageCodes.DOWNLOAD_DIR_CREATE_FAILED, target.getParent());
    }
  }

  private static Path createSiblingTempFile(Path target) {
    try {
      Path dir = target.getParent() != null ? target.getParent() : Path.of(".");
      String base = target.getFileName().toString();
      return Files.createTempFile(dir, "." + base + ".", ".part");
    } catch (IOException e) {
      throw new AppException(MessageCodes.DOWNLOAD_TMP_CREATE_FAILED, target);
    }
  }

  private static void moveAtomicallyOrReplace(Path tmp, Path target) {
    try {
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      try {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ex) {
        throw new AppException(MessageCodes.DOWNLOAD_MOVE_FAILED, tmp, target);
      }
    } catch (IOException e) {
      throw new AppException(MessageCodes.DOWNLOAD_MOVE_FAILED, tmp, target);
    }
  }

  private static void safeDelete(Path p) {
    try {
      if (p != null) {
        Files.deleteIfExists(p);
      }
    } catch (IOException ignore) {
    }
  }
}
