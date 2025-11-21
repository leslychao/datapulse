package io.datapulse.etl.file;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class DefaultSnapshotCommitBarrier implements SnapshotCommitBarrier {

  private static final class SnapshotState {

    final Path file;
    final String requestId;
    final Long accountId;
    final MarketplaceEvent event;
    final MarketplaceType marketplace;
    final String sourceId;

    final AtomicInteger batches = new AtomicInteger(0);
    volatile boolean snapshotCompleted = false;
    volatile boolean hasElements = false;

    SnapshotState(
        Path file,
        String requestId,
        Long accountId,
        MarketplaceEvent event,
        MarketplaceType marketplace,
        String sourceId
    ) {
      this.file = file;
      this.requestId = requestId;
      this.accountId = accountId;
      this.event = event;
      this.marketplace = marketplace;
      this.sourceId = sourceId;
    }

    boolean isReadyToComplete() {
      if (!hasElements) {
        return snapshotCompleted;
      }
      return snapshotCompleted && batches.get() == 0;
    }
  }

  private final Map<String, SnapshotState> snapshots = new ConcurrentHashMap<>();

  @Override
  public String registerSnapshot(
      Path file,
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      MarketplaceType marketplace,
      String sourceId
  ) {
    Objects.requireNonNull(file, "file must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(event, "event must not be null");
    Objects.requireNonNull(marketplace, "marketplace must not be null");
    Objects.requireNonNull(sourceId, "sourceId must not be null");

    String snapshotId = UUID.randomUUID().toString();
    SnapshotState state = new SnapshotState(
        file,
        requestId,
        accountId,
        event,
        marketplace,
        sourceId
    );
    snapshots.put(snapshotId, state);

    log.debug("Snapshot registered: id={}, file={}", snapshotId, file);
    return snapshotId;
  }

  @Override
  public void registerFirstElement(String snapshotId) {
    SnapshotState state = findStateOrWarn(snapshotId, "registerFirstElement");
    if (state == null) {
      return;
    }

    state.hasElements = true;
    log.trace("registerFirstElement(): snapshotId={}", snapshotId);
  }

  @Override
  public void registerBatch(String snapshotId) {
    SnapshotState state = findStateOrWarn(snapshotId, "registerBatch");
    if (state == null) {
      return;
    }

    int value = state.batches.incrementAndGet();
    log.trace("registerBatch(): snapshotId={}, batches={}", snapshotId, value);
  }

  @Override
  public void batchCompleted(String snapshotId) {
    SnapshotState state = findStateOrWarn(snapshotId, "batchCompleted");
    if (state == null) {
      return;
    }

    int remaining = state.batches.decrementAndGet();
    if (remaining < 0) {
      state.batches.set(0);
      log.error("batchCompleted(): negative batch counter for snapshotId={}", snapshotId);
    } else {
      log.trace("batchCompleted(): snapshotId={}, remaining={}", snapshotId, remaining);
    }

    tryComplete(snapshotId, state);
  }

  @Override
  public void snapshotCompleted(String snapshotId) {
    SnapshotState state = findStateOrWarn(snapshotId, "snapshotCompleted");
    if (state == null) {
      return;
    }

    state.snapshotCompleted = true;
    log.trace(
        "snapshotCompleted(): snapshotId={}, hasElements={}, batches={}",
        snapshotId,
        state.hasElements,
        state.batches.get()
    );

    if (!state.hasElements) {
      tryComplete(snapshotId, state);
    }
  }

  @Override
  public void discard(String snapshotId, Path providedFile) {
    Path fileToDelete = providedFile;

    SnapshotState removed = snapshotId != null ? snapshots.remove(snapshotId) : null;
    if (removed != null) {
      fileToDelete = removed.file != null ? removed.file : providedFile;
      log.warn(
          "Snapshot discarded: id={}, file={}, requestId={}, accountId={}, event={}, marketplace={}, sourceId={}",
          snapshotId,
          fileToDelete,
          removed.requestId,
          removed.accountId,
          removed.event,
          removed.marketplace,
          removed.sourceId
      );
    } else if (snapshotId == null) {
      log.warn("discard(): null snapshotId, file={}", providedFile);
    } else {
      log.warn("discard(): unknown snapshotId={}, file={}", snapshotId, providedFile);
    }

    if (fileToDelete != null) {
      deleteFile(fileToDelete, "discard");
    }
  }

  private SnapshotState findStateOrWarn(String snapshotId, String operation) {
    SnapshotState state = snapshots.get(snapshotId);
    if (state == null) {
      log.error("{}(): unknown snapshotId={}", operation, snapshotId);
    }
    return state;
  }

  private void tryComplete(String snapshotId, SnapshotState state) {
    if (!state.isReadyToComplete()) {
      return;
    }

    SnapshotState removed = snapshots.remove(snapshotId);
    if (removed != state) {
      log.trace("tryComplete(): state already removed or changed for snapshotId={}", snapshotId);
      return;
    }

    deleteFile(removed.file, "complete");

    log.info(
        "Snapshot fully completed: id={}, file={}, requestId={}, accountId={}, event={}, marketplace={}, sourceId={}",
        snapshotId,
        removed.file,
        removed.requestId,
        removed.accountId,
        removed.event,
        removed.marketplace,
        removed.sourceId
    );
  }

  private void deleteFile(Path file, String reason) {
    try {
      boolean deleted = Files.deleteIfExists(file);
      log.debug("Snapshot file delete: file={}, deleted={}, reason={}", file, deleted, reason);
    } catch (IOException ex) {
      log.warn("Snapshot file delete failed: file={}, reason={}", file, reason, ex);
    }
  }
}
