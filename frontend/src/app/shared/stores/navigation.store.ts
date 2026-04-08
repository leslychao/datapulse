import { untracked } from '@angular/core';
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
      const current = untracked(store.lastTabByModule);
      if (current[module] === path) return;
      patchState(store, {
        lastTabByModule: { ...current, [module]: path },
      });
    },

    getLastTab(module: string): string | null {
      return store.lastTabByModule()[module] ?? null;
    },

    setSectionFilter(key: string, filters: Record<string, any>): void {
      const current = untracked(store.sectionFilters);
      const existing = current[key];
      if (existing && JSON.stringify(existing) === JSON.stringify(filters)) return;
      patchState(store, {
        sectionFilters: { ...current, [key]: filters },
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

    persist(workspaceId: number): void {
      const payload = {
        lastTabByModule: store.lastTabByModule(),
        sectionFilters: store.sectionFilters(),
      };
      try {
        localStorage.setItem(storageKey(workspaceId), JSON.stringify(payload));
      } catch { /* storage full — ignore */ }
    },

    restore(workspaceId: number): void {
      try {
        const raw = localStorage.getItem(storageKey(workspaceId));
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
