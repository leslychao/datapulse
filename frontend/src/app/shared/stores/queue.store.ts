import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';

import { QueueFilter } from '@core/models/queue.model';

export interface QueueStoreState {
  selectedQueueId: number | null;
  itemFilters: QueueFilter;
}

const initialState: QueueStoreState = {
  selectedQueueId: null,
  itemFilters: {},
};

export const QueueStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    hasSelectedQueue: computed(() => store.selectedQueueId() !== null),
  })),
  withMethods((store) => ({
    selectQueue(queueId: number): void {
      patchState(store, { selectedQueueId: queueId, itemFilters: {} });
    },
    setFilters(filters: QueueFilter): void {
      patchState(store, { itemFilters: filters });
    },
    resetFilters(): void {
      patchState(store, { itemFilters: {} });
    },
  })),
);
