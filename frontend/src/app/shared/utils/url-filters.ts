import { computed, effect, Signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

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
    const params: Record<string, string> = {};
    for (const def of defs) {
      const value = def.signal();
      if (value !== '' && value !== def.defaultValue) {
        params[def.key] = value;
      }
    }
    router.navigate([], {
      relativeTo: route,
      queryParams: params,
      queryParamsHandling: 'replace',
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
