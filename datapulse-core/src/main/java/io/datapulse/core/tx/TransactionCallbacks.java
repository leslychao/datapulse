package io.datapulse.core.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionCallbacks {

  private TransactionCallbacks() {
  }

  public static void afterRollback(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              action.run();
            }
          }
        }
    );
  }

  public static void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        }
    );
  }
}
