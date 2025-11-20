package io.datapulse.etl.file;

import io.datapulse.domain.MarketplaceEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class DefaultSnapshotCommitBarrier implements SnapshotCommitBarrier {

  private static final class SnapshotState {

    final Path file;
    final String requestId;
    final Long accountId;
    final MarketplaceEvent event;

    final AtomicInteger batches = new AtomicInteger(0);
    volatile boolean snapshotCompleted = false;
    volatile boolean hasElements = false;

    SnapshotState(Path file, String requestId, Long accountId, MarketplaceEvent event) {
      this.file = file;
      this.requestId = requestId;
      this.accountId = accountId;
      this.event = event;
    }

    boolean isReadyToComplete() {
      return snapshotCompleted && batches.get() == 0;
    }
  }

  private final Map<String, SnapshotState> snapshots = new ConcurrentHashMap<>();

  private final List<SnapshotCompletionListener> listeners = new CopyOnWriteArrayList<>();

  @Override
  public String registerSnapshot(
      Path file,
      String requestId,
      Long accountId,
      MarketplaceEvent event
  ) {
    Objects.requireNonNull(file, "file must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(event, "event must not be null");

    String snapshotId = UUID.randomUUID().toString();
    snapshots.put(snapshotId, new SnapshotState(file, requestId, accountId, event));

    log.debug("Snapshot registered: id={}, file={}", snapshotId, file);
    return snapshotId;
  }

  @Override
  public void registerFirstElement(String snapshotId) {
    SnapshotState state = snapshots.get(snapshotId);
    if (state == null) {
      log.error("registerFirstElement(): unknown snapshotId={}", snapshotId);
      return;
    }
    state.hasElements = true;
  }

  @Override
  public void registerBatch(String snapshotId) {
    SnapshotState state = snapshots.get(snapshotId);
    if (state == null) {
      log.error("registerBatch(): unknown snapshotId={}", snapshotId);
      return;
    }
    state.batches.incrementAndGet();
  }

  @Override
  public void batchCompleted(String snapshotId) {
    SnapshotState state = snapshots.get(snapshotId);
    if (state == null) {
      log.error("batchCompleted(): unknown snapshotId={}", snapshotId);
      return;
    }

    int remaining = state.batches.decrementAndGet();
    if (remaining < 0) {
      state.batches.set(0);
      log.error("batchCompleted(): negative batch counter for snapshotId={}", snapshotId);
    }

    tryComplete(snapshotId, state);
  }

  @Override
  public void snapshotCompleted(String snapshotId) {
    SnapshotState state = snapshots.get(snapshotId);
    if (state == null) {
      log.error("snapshotCompleted(): unknown snapshotId={}", snapshotId);
      return;
    }

    state.snapshotCompleted = true;

    if (!state.hasElements) {
      tryComplete(snapshotId, state);
    }
  }

  private void tryComplete(String snapshotId, SnapshotState state) {
    if (!state.isReadyToComplete()) {
      return;
    }

    SnapshotState removed = snapshots.remove(snapshotId);
    if (removed != state) {
      return;
    }

    deleteFile(removed.file, "complete");

    log.info(
        "Snapshot fully completed: id={}, file={}, requestId={}, accountId={}, event={}",
        snapshotId,
        removed.file,
        removed.requestId,
        removed.accountId,
        removed.event
    );

    SnapshotCompletionEvent event = new SnapshotCompletionEvent(
        snapshotId,
        removed.file,
        removed.requestId,
        removed.accountId,
        removed.event
    );

    for (SnapshotCompletionListener listener : listeners) {
      try {
        listener.onSnapshotCompleted(event);
      } catch (Exception ex) {
        log.warn(
            "SnapshotCompletionListener failed: snapshotId={}, listener={}",
            snapshotId,
            listener,
            ex
        );
      }
    }
  }

  @Override
  public void discard(String snapshotId, Path providedFile) {
    Path fileToDelete = providedFile;

    SnapshotState removed = snapshotId != null ? snapshots.remove(snapshotId) : null;
    if (removed != null) {
      fileToDelete = removed.file != null ? removed.file : providedFile;
      log.warn("Snapshot discarded: id={}, file={}", snapshotId, fileToDelete);
    } else if (snapshotId == null) {
      log.warn("discard(): null snapshotId, file={}", providedFile);
    } else {
      log.warn("discard(): unknown snapshotId={}, file={}", snapshotId, providedFile);
    }

    if (fileToDelete != null) {
      deleteFile(fileToDelete, "discard");
    }
  }

  private void deleteFile(Path file, String reason) {
    try {
      boolean deleted = Files.deleteIfExists(file);
      log.debug("Snapshot file delete: file={}, deleted={}, reason={}", file, deleted, reason);
    } catch (IOException ex) {
      log.warn("Snapshot file delete failed: file={}, reason={}", file, reason, ex);
    }
  }

  @Override
  public void registerListener(SnapshotCompletionListener listener) {
    if (listener == null) {
      return;
    }
    listeners.add(listener);
    log.debug("SnapshotCompletionListener registered: {}", listener);
  }
}
