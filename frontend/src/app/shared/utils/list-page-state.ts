import { computed, inject, signal, Signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import {
  FilterBarUrlDef,
  readFilterBarFromUrl,
  syncFilterBarToUrl,
  isFilterBarDefault,
  SortUrlState,
  readSortFromUrl,
  syncSortToUrl,
  UrlFilterDef,
  readFiltersFromUrl,
  syncFiltersToUrl,
  isFiltersDefault,
  resetFilters,
} from './url-filters';

export interface ListPageStateConfig {
  defaultSort: SortUrlState;
  defaultPageSize?: number;
  filterBarDefs?: FilterBarUrlDef[];
  filterDefs?: UrlFilterDef[];
  syncToUrl?: boolean;
}

export interface ListPageState {
  readonly filterValues: WritableSignal<Record<string, any>>;
  readonly currentPage: WritableSignal<number>;
  readonly currentSort: WritableSignal<SortUrlState>;
  readonly pageSize: WritableSignal<number>;

  readonly queryDeps: Signal<unknown[]>;
  readonly hasActiveFilters: Signal<boolean>;
  readonly sortParam: Signal<string>;
  readonly initialSortModel: Signal<Array<{ colId: string; sort: 'asc' | 'desc' }>>;

  onFiltersChanged(values: Record<string, any>): void;
  onSortChanged(event: { column: string; direction: string }): void;
  onPageChanged(event: { page: number; pageSize: number }): void;
  resetFilters(): void;
}

/**
 * Creates a unified state bundle for list/table pages.
 * Must be called from an injection context (constructor or field initializer).
 *
 * Handles filter/sort/page signals, URL synchronization, and provides
 * ready-made handlers to wire into dp-data-grid and dp-pagination-bar.
 */
export function createListPageState(config: ListPageStateConfig): ListPageState {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const syncToUrl = config.syncToUrl !== false;

  const filterValues = signal<Record<string, any>>({});
  const currentPage = signal(0);
  const currentSort = signal<SortUrlState>(config.defaultSort);
  const pageSize = signal(config.defaultPageSize ?? 50);

  if (syncToUrl) {
    if (config.filterBarDefs) {
      readFilterBarFromUrl(route, filterValues, config.filterBarDefs);
      syncFilterBarToUrl(router, route, filterValues, config.filterBarDefs);
    }
    if (config.filterDefs) {
      readFiltersFromUrl(route, config.filterDefs);
      syncFiltersToUrl(router, route, config.filterDefs);
    }
    readSortFromUrl(route, currentSort);
    syncSortToUrl(router, route, currentSort, config.defaultSort);
  }

  const hasActiveFilters: Signal<boolean> = config.filterBarDefs
    ? computed(() => !isFilterBarDefault(filterValues)())
    : config.filterDefs
      ? isFiltersDefault(config.filterDefs)
      : computed(() => false);

  const sortParam = computed(() => {
    const s = currentSort();
    return `${s.column},${s.direction}`;
  });

  const initialSortModel = computed((): Array<{ colId: string; sort: 'asc' | 'desc' }> => {
    const s = currentSort();
    if (!s.column) {
      return [];
    }
    const direction = s.direction === 'asc' ? 'asc' : 'desc';
    return [{ colId: s.column, sort: direction }];
  });

  const queryDeps = computed(() => [
    filterValues(),
    currentPage(),
    currentSort(),
    pageSize(),
  ]);

  return {
    filterValues,
    currentPage,
    currentSort,
    pageSize,
    queryDeps,
    hasActiveFilters,
    sortParam,
    initialSortModel,

    onFiltersChanged(values: Record<string, any>): void {
      filterValues.set(values);
      currentPage.set(0);
    },

    onSortChanged(event: { column: string; direction: string }): void {
      if (!event.column) return;
      currentSort.set({
        column: event.column,
        direction: event.direction === 'asc' ? 'asc' : 'desc',
      });
      currentPage.set(0);
    },

    onPageChanged(event: { page: number; pageSize: number }): void {
      currentPage.set(event.page);
      pageSize.set(event.pageSize);
    },

    resetFilters(): void {
      filterValues.set({});
      if (config.filterDefs) {
        resetFilters(config.filterDefs);
      }
      currentPage.set(0);
    },
  };
}
