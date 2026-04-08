import { computed, effect, Signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

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
  effect(() => {
    const sort = source();
    const isDefault =
      sort.column === defaultSort.column &&
      sort.direction === defaultSort.direction;
    const params: Record<string, string | null> = {
      sortBy: isDefault ? null : sort.column,
      sortDir: isDefault ? null : sort.direction,
    };
    router.navigate([], {
      relativeTo: route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
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
  effect(() => {
    const params: Record<string, string | null> = {};
    for (const def of defs) {
      const value = def.signal();
      params[def.key] =
        value !== '' && value !== def.defaultValue ? value : null;
    }
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
