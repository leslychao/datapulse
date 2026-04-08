import { computed, effect, inject, Signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ViewStateService } from '@shared/services/view-state.service';

// ---------------------------------------------------------------------------
// Sort URL persistence
// ---------------------------------------------------------------------------

export interface SortUrlState {
  column: string;
  direction: 'asc' | 'desc';
}

/**
 * Reads sort state from URL query params (sortBy, sortDir).
 * Call from constructor or field initializer.
 */
export function readSortFromUrl(
  route: ActivatedRoute,
  target: WritableSignal<SortUrlState>,
): void {
  const qp = route.snapshot.queryParams;
  const sortBy = qp['sortBy'];
  const sortDir = qp['sortDir'];
  if (sortBy) {
    target.set({
      column: sortBy,
      direction: sortDir === 'asc' ? 'asc' : 'desc',
    });
  }
}

/**
 * Creates an effect that writes sort state to URL (sortBy, sortDir).
 * Default sort is omitted from URL to keep it clean.
 * Must be called from an injection context.
 */
export function syncSortToUrl(
  router: Router,
  route: ActivatedRoute,
  source: Signal<SortUrlState>,
  defaultSort: SortUrlState,
): void {
  let lastSerialized = '';
  effect(() => {
    const sort = source();
    const isDefault =
      sort.column === defaultSort.column &&
      sort.direction === defaultSort.direction;
    const params: Record<string, string | null> = {
      sortBy: isDefault ? null : sort.column,
      sortDir: isDefault ? null : sort.direction,
    };
    const serialized = JSON.stringify(params);
    if (serialized === lastSerialized) return;
    lastSerialized = serialized;
    router.navigate([], {
      relativeTo: route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
}

/**
 * Returns true if any of the given query-param keys are present in the URL.
 * Used to decide whether URL or persisted localStorage state takes priority.
 */
export function hasUrlState(route: ActivatedRoute, keys: string[]): boolean {
  const qp = route.snapshot.queryParams;
  return keys.some((k) => qp[k] != null && qp[k] !== '');
}

export interface UrlFilterDef {
  key: string;
  signal: WritableSignal<string>;
  defaultValue: string;
}

/**
 * Reads URL query params and sets matching signal values.
 * Call from constructor or ngOnInit.
 */
export function readFiltersFromUrl(
  route: ActivatedRoute,
  defs: UrlFilterDef[],
): void {
  const qp = route.snapshot.queryParams;
  for (const def of defs) {
    const raw = qp[def.key];
    if (raw != null && raw !== '') {
      def.signal.set(raw);
    }
  }
}

/**
 * Creates an effect that syncs signal values back to URL query params.
 * Must be called from an injection context (constructor or field initializer).
 * Only non-default values are written to URL.
 */
export function syncFiltersToUrl(
  router: Router,
  route: ActivatedRoute,
  defs: UrlFilterDef[],
): void {
  let lastSerialized = '';
  effect(() => {
    const params: Record<string, string | null> = {};
    for (const def of defs) {
      const value = def.signal();
      params[def.key] =
        value !== '' && value !== def.defaultValue ? value : null;
    }
    const serialized = JSON.stringify(params);
    if (serialized === lastSerialized) return;
    lastSerialized = serialized;
    router.navigate([], {
      relativeTo: route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
}

/**
 * Returns a computed signal that is true when all filters are at their default values.
 */
export function isFiltersDefault(defs: UrlFilterDef[]): Signal<boolean> {
  return computed(() => defs.every((d) => d.signal() === d.defaultValue));
}

/**
 * Resets all filter signals to their default values.
 */
export function resetFilters(defs: UrlFilterDef[]): void {
  for (const def of defs) {
    def.signal.set(def.defaultValue);
  }
}

// ---------------------------------------------------------------------------
// Filter-bar adapter (works with Record<string, any> signal)
// ---------------------------------------------------------------------------

export type FilterBarFieldType = 'string' | 'csv' | 'date-range';

export interface FilterBarUrlDef {
  key: string;
  type: FilterBarFieldType;
}

/**
 * Restores filter-bar values from URL query params on init.
 */
export function readFilterBarFromUrl(
  route: ActivatedRoute,
  target: WritableSignal<Record<string, any>>,
  defs: FilterBarUrlDef[],
): void {
  const qp = route.snapshot.queryParams;
  const patch: Record<string, any> = { ...target() };
  let changed = false;
  for (const def of defs) {
    if (def.type === 'date-range') {
      const from = qp[def.key + '_from'];
      const to = qp[def.key + '_to'];
      if (from || to) {
        patch[def.key] = { from: from ?? '', to: to ?? '' };
        changed = true;
      }
    } else if (def.type === 'csv') {
      const raw = qp[def.key];
      if (raw) {
        patch[def.key] = raw.split(',');
        changed = true;
      }
    } else {
      const raw = qp[def.key];
      if (raw != null && raw !== '') {
        patch[def.key] = raw;
        changed = true;
      }
    }
  }
  if (changed) {
    target.set(patch);
  }
}

/**
 * Creates an effect that writes filter-bar values to URL query params.
 * Must be called from an injection context.
 */
export function syncFilterBarToUrl(
  router: Router,
  route: ActivatedRoute,
  source: Signal<Record<string, any>>,
  defs: FilterBarUrlDef[],
): void {
  let lastSerialized = '';
  effect(() => {
    const vals = source();
    const params: Record<string, string | null> = {};
    for (const def of defs) {
      if (def.type === 'date-range') {
        const range = vals[def.key];
        params[def.key + '_from'] = range?.from || null;
        params[def.key + '_to'] = range?.to || null;
      } else if (def.type === 'csv') {
        const arr = vals[def.key];
        params[def.key] =
          Array.isArray(arr) && arr.length > 0 ? arr.join(',') : null;
      } else {
        const v = vals[def.key];
        params[def.key] = v != null && v !== '' ? String(v) : null;
      }
    }
    const serialized = JSON.stringify(params);
    if (serialized === lastSerialized) return;
    lastSerialized = serialized;
    router.navigate([], {
      relativeTo: route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
}

/**
 * Returns a computed signal that is true when filter-bar has no active values.
 */
export function isFilterBarDefault(
  source: Signal<Record<string, any>>,
): Signal<boolean> {
  return computed(() => {
    const vals = source();
    return Object.values(vals).every(
      (v) =>
        v === '' ||
        v === null ||
        v === undefined ||
        (Array.isArray(v) && v.length === 0) ||
        (typeof v === 'object' && v !== null && !Array.isArray(v) && !v.from && !v.to),
    );
  });
}

// ---------------------------------------------------------------------------
// Persisted URL filter integration
// ---------------------------------------------------------------------------

export interface PersistedFilterConfig {
  pageKey: string;
  filterDefs: UrlFilterDef[];
  sortSignal?: WritableSignal<SortUrlState>;
  defaultSort?: SortUrlState;
}

/**
 * Wires up URL filter/sort sync with ViewStateService persistence.
 * Restores from localStorage when URL has no query params.
 * Must be called from an injection context (constructor or field initializer).
 */
export function initPersistedFilters(
  router: Router,
  route: ActivatedRoute,
  config: PersistedFilterConfig,
): void {
  const viewState = inject(ViewStateService);
  const urlKeys = config.filterDefs.map((d) => d.key);
  if (config.sortSignal) urlKeys.push('sortBy', 'sortDir');

  const urlPresent = hasUrlState(route, urlKeys);

  if (!urlPresent) {
    const persisted = viewState.restore(config.pageKey);
    if (persisted?.filters) {
      for (const def of config.filterDefs) {
        const val = persisted.filters[def.key];
        if (val != null && val !== '') def.signal.set(val);
      }
    }
    if (config.sortSignal && persisted?.sort) {
      config.sortSignal.set(persisted.sort);
    }
  }

  if (urlPresent) readFiltersFromUrl(route, config.filterDefs);
  syncFiltersToUrl(router, route, config.filterDefs);

  if (config.sortSignal) {
    if (urlPresent) readSortFromUrl(route, config.sortSignal);
    if (config.defaultSort) {
      syncSortToUrl(router, route, config.sortSignal, config.defaultSort);
    }
  }

  effect(() => {
    const filters: Record<string, any> = {};
    for (const def of config.filterDefs) {
      filters[def.key] = def.signal();
    }
    const sort = config.sortSignal?.() ?? undefined;
    viewState.save(config.pageKey, { filters, sort });
  });
}
