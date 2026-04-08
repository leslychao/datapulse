import { computed } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';

export interface NavigationState {
  lastTabByModule: Record<string, string>;
  sectionFilters: Record<string, Record<string, any>>;
}

const initialState: NavigationState = {
  lastTabByModule: {},
  sectionFilters: {},
};

function storageKey(workspaceId: number): string {
  return `dp:nav:${workspaceId}`;
}

export const NavigationStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store) => ({
    setLastTab(module: string, path: string): void {
      patchState(store, {
        lastTabByModule: { ...store.lastTabByModule(), [module]: path },
      });
    },

    getLastTab(module: string): string | null {
      return store.lastTabByModule()[module] ?? null;
    },

    setSectionFilter(key: string, filters: Record<string, any>): void {
      patchState(store, {
        sectionFilters: { ...store.sectionFilters(), [key]: filters },
      });
    },

    getSectionFilter(key: string): Record<string, any> {
      return store.sectionFilters()[key] ?? {};
    },

    getSectionFilterValue<T = string>(key: string, field: string): T | null {
      const filters = store.sectionFilters()[key];
      if (!filters) return null;
      return (filters[field] as T) ?? null;
    },

    persistToSession(workspaceId: number): void {
      const payload = {
        lastTabByModule: store.lastTabByModule(),
        sectionFilters: store.sectionFilters(),
      };
      try {
        sessionStorage.setItem(storageKey(workspaceId), JSON.stringify(payload));
      } catch { /* storage full — ignore */ }
    },

    restoreFromSession(workspaceId: number): void {
      try {
        const raw = sessionStorage.getItem(storageKey(workspaceId));
        if (!raw) return;
        const data = JSON.parse(raw) as Partial<NavigationState>;
        patchState(store, {
          lastTabByModule: data.lastTabByModule ?? {},
          sectionFilters: data.sectionFilters ?? {},
        });
      } catch { /* corrupted — ignore */ }
    },

    clearAll(): void {
      patchState(store, initialState);
    },
  })),
);
