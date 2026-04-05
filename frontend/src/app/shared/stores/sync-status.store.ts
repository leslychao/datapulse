import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';

import { ConnectionSyncStatus, SyncHealth } from '@core/models';

export interface SyncStatusState {
  connections: ConnectionSyncStatus[];
  lastUpdated: string | null;
}

const initialState: SyncStatusState = {
  connections: [],
  lastUpdated: null,
};

export const SyncStatusStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store) => ({
    setConnections(connections: ConnectionSyncStatus[]): void {
      patchState(store, { connections });
    },
    updateConnection(connectionId: number, partial: Partial<ConnectionSyncStatus>): void {
      patchState(store, {
        connections: store.connections().map((c) =>
          c.connectionId === connectionId ? { ...c, ...partial } : c,
        ),
      });
    },
    upsertConnection(conn: ConnectionSyncStatus): void {
      const list = store.connections();
      const idx = list.findIndex((c) => c.connectionId === conn.connectionId);
      if (idx === -1) {
        patchState(store, { connections: [...list, conn] });
      } else {
        patchState(store, {
          connections: list.map((c, i) => (i === idx ? { ...c, ...conn } : c)),
        });
      }
    },
    setLastUpdated(lastUpdated: string): void {
      patchState(store, { lastUpdated });
    },
  })),
);
