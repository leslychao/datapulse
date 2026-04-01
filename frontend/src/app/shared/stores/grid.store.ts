import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';

import { DraftPriceChange, OfferFilter } from '@core/models';

export interface GridStoreState {
  selectedViewId: number | null;
  filters: OfferFilter;
  sortColumn: string | null;
  sortDirection: 'ASC' | 'DESC';
  page: number;
  pageSize: number;
  selectedOfferIds: Set<number>;
  draftMode: boolean;
  draftChanges: Map<number, DraftPriceChange>;
  searchTerm: string;
  rowDensity: 'compact' | 'comfortable';
}

const initialState: GridStoreState = {
  selectedViewId: null,
  filters: {},
  sortColumn: 'skuCode',
  sortDirection: 'ASC',
  page: 0,
  pageSize: 50,
  selectedOfferIds: new Set<number>(),
  draftMode: false,
  draftChanges: new Map<number, DraftPriceChange>(),
  searchTerm: '',
  rowDensity: 'compact',
};

export const GridStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    activeFilters: computed(() => {
      const f = store.filters();
      return Object.entries(f).filter(
        ([, v]) => v !== undefined && v !== null && v !== '' &&
          !(Array.isArray(v) && v.length === 0),
      );
    }),
    hasActiveFilters: computed(() => {
      const f = store.filters();
      return Object.values(f).some(
        (v) => v !== undefined && v !== null && v !== '' &&
          !(Array.isArray(v) && v.length === 0),
      );
    }),
    hasSelection: computed(() => store.selectedOfferIds().size > 0),
    selectedCount: computed(() => store.selectedOfferIds().size),
    rowHeight: computed(() => store.rowDensity() === 'compact' ? 32 : 40),
    draftCount: computed(() => store.draftChanges().size),
    hasDraftChanges: computed(() => store.draftChanges().size > 0),
  })),
  withMethods((store) => ({
    setView(viewId: number, filters: OfferFilter, sortColumn: string | null, sortDirection: 'ASC' | 'DESC'): void {
      patchState(store, {
        selectedViewId: viewId,
        filters,
        sortColumn,
        sortDirection,
        page: 0,
        selectedOfferIds: new Set<number>(),
      });
    },

    updateFilters(filters: OfferFilter): void {
      patchState(store, { filters, page: 0 });
    },

    updateFilter<K extends keyof OfferFilter>(key: K, value: OfferFilter[K]): void {
      patchState(store, {
        filters: { ...store.filters(), [key]: value },
        page: 0,
      });
    },

    resetFilters(): void {
      patchState(store, { filters: {}, page: 0 });
    },

    setSort(column: string, direction: 'ASC' | 'DESC'): void {
      patchState(store, { sortColumn: column, sortDirection: direction, page: 0 });
    },

    setPage(page: number): void {
      patchState(store, { page });
    },

    setPageSize(pageSize: number): void {
      patchState(store, { pageSize, page: 0 });
    },

    selectOffers(ids: number[]): void {
      patchState(store, { selectedOfferIds: new Set(ids) });
    },

    clearSelection(): void {
      patchState(store, { selectedOfferIds: new Set<number>() });
    },

    toggleDraftMode(): void {
      const enabling = !store.draftMode();
      if (!enabling && store.draftChanges().size > 0) {
        return;
      }
      patchState(store, { draftMode: enabling });
    },

    setDraftMode(enabled: boolean): void {
      patchState(store, {
        draftMode: enabled,
        ...(!enabled ? { draftChanges: new Map<number, DraftPriceChange>() } : {}),
      });
    },

    setDraftPrice(offerId: number, newPrice: number, originalPrice: number): void {
      const next = new Map(store.draftChanges());
      next.set(offerId, { offerId, newPrice, originalPrice });
      patchState(store, { draftChanges: next });
    },

    removeDraftPrice(offerId: number): void {
      const next = new Map(store.draftChanges());
      next.delete(offerId);
      patchState(store, { draftChanges: next });
    },

    clearDraftChanges(): void {
      patchState(store, { draftChanges: new Map<number, DraftPriceChange>() });
    },

    setSearchTerm(term: string): void {
      patchState(store, {
        searchTerm: term,
        filters: {
          ...store.filters(),
          skuCode: term || undefined,
        },
        page: 0,
      });
    },

    toggleDensity(): void {
      patchState(store, {
        rowDensity: store.rowDensity() === 'compact' ? 'comfortable' : 'compact',
      });
    },
  })),
);
