import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';

import { QueueFilter, QueueItemStatus } from '@core/models/queue.model';

export interface QueueStoreState {
  selectedQueueId: number | null;
  itemFilters: QueueFilter;
  pageIndex: number;
  pageSize: number;
  selectedItemIds: Set<number>;
}

const initialState: QueueStoreState = {
  selectedQueueId: null,
  itemFilters: {},
  pageIndex: 0,
  pageSize: 20,
  selectedItemIds: new Set<number>(),
};

export const QueueStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    hasSelectedQueue: computed(() => store.selectedQueueId() !== null),
    hasSelection: computed(() => store.selectedItemIds().size > 0),
    selectionCount: computed(() => store.selectedItemIds().size),
  })),
  withMethods((store) => ({
    selectQueue(queueId: number): void {
      patchState(store, {
        selectedQueueId: queueId,
        itemFilters: {},
        pageIndex: 0,
        selectedItemIds: new Set<number>(),
      });
    },
    setFilters(filters: QueueFilter): void {
      patchState(store, { itemFilters: filters, pageIndex: 0 });
    },
    patchFilter(patch: Partial<QueueFilter>): void {
      const current = store.itemFilters();
      patchState(store, { itemFilters: { ...current, ...patch }, pageIndex: 0 });
    },
    resetFilters(): void {
      patchState(store, { itemFilters: {}, pageIndex: 0 });
    },
    setPage(page: number): void {
      patchState(store, { pageIndex: page });
    },
    setPageSize(size: number): void {
      patchState(store, { pageSize: size, pageIndex: 0 });
    },
    setSelectedItemIds(ids: Set<number>): void {
      patchState(store, { selectedItemIds: ids });
    },
    clearSelection(): void {
      patchState(store, { selectedItemIds: new Set<number>() });
    },
  })),
);
