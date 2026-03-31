import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';

export type SyncHealth = 'OK' | 'STALE' | 'ERROR';

export interface ConnectionSyncStatus {
  connectionId: number;
  connectionName: string;
  lastSuccessAt: string | null;
  status: SyncHealth;
}

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
    setLastUpdated(lastUpdated: string): void {
      patchState(store, { lastUpdated });
    },
  })),
);
