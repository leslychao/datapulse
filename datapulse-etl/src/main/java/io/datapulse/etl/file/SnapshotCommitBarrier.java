package io.datapulse.etl.file;

import io.datapulse.domain.MarketplaceEvent;
import java.nio.file.Path;

public interface SnapshotCommitBarrier {

  /**
   * Событие "снапшот полностью завершён":
   * - все батчи сохранены,
   * - stream закрыт,
   * - файл удалён (или попытка удаления была).
   */
  record SnapshotCompletionEvent(
      String snapshotId,
      Path file,
      String requestId,
      Long accountId,
      MarketplaceEvent event
  ) {
  }

  @FunctionalInterface
  interface SnapshotCompletionListener {
    void onSnapshotCompleted(SnapshotCompletionEvent event);
  }

  String registerSnapshot(
      Path file,
      String requestId,
      Long accountId,
      MarketplaceEvent event
  );

  void registerFirstElement(String snapshotId);

  void registerBatch(String snapshotId);

  void batchCompleted(String snapshotId);

  void snapshotCompleted(String snapshotId);

  void discard(String snapshotId, Path providedFile);

  /**
   * Регистрирует слушателя, который будет вызван, когда снапшот
   * реально завершён (isReadyToComplete() == true).
   */
  void registerListener(SnapshotCompletionListener listener);
}
