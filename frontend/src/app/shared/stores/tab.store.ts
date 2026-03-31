import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';

export interface TabItem {
  id: string;
  label: string;
  route: string;
  pinned: boolean;
  closeable: boolean;
  viewId?: number;
}

export interface TabState {
  tabsByModule: Record<string, TabItem[]>;
  activeTabByModule: Record<string, string | null>;
}

const initialState: TabState = {
  tabsByModule: {},
  activeTabByModule: {},
};

function storageKey(workspaceId: number, module: string): string {
  return `dp:tabs:${workspaceId}:${module}`;
}

export const TabStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    allModules: computed(() => Object.keys(store.tabsByModule())),
  })),
  withMethods((store) => ({
    getTabs(module: string): TabItem[] {
      return store.tabsByModule()[module] ?? [];
    },

    getActiveTab(module: string): string | null {
      return store.activeTabByModule()[module] ?? null;
    },

    addTab(module: string, tab: TabItem): void {
      const current = store.tabsByModule()[module] ?? [];
      if (current.some((t) => t.id === tab.id)) {
        patchState(store, {
          activeTabByModule: { ...store.activeTabByModule(), [module]: tab.id },
        });
        return;
      }
      patchState(store, {
        tabsByModule: { ...store.tabsByModule(), [module]: [...current, tab] },
        activeTabByModule: { ...store.activeTabByModule(), [module]: tab.id },
      });
    },

    removeTab(module: string, tabId: string): void {
      const current = store.tabsByModule()[module] ?? [];
      const filtered = current.filter((t) => t.id !== tabId);
      const activeId = store.activeTabByModule()[module];
      const newActive = activeId === tabId
        ? (filtered.length > 0 ? filtered[filtered.length - 1].id : null)
        : activeId;
      patchState(store, {
        tabsByModule: { ...store.tabsByModule(), [module]: filtered },
        activeTabByModule: { ...store.activeTabByModule(), [module]: newActive },
      });
    },

    setActiveTab(module: string, tabId: string): void {
      patchState(store, {
        activeTabByModule: { ...store.activeTabByModule(), [module]: tabId },
      });
    },

    reorderTabs(module: string, tabs: TabItem[]): void {
      patchState(store, {
        tabsByModule: { ...store.tabsByModule(), [module]: tabs },
      });
    },

    persistToSession(workspaceId: number): void {
      const modules = store.tabsByModule();
      const active = store.activeTabByModule();
      for (const module of Object.keys(modules)) {
        const payload = { tabs: modules[module], activeTab: active[module] ?? null };
        sessionStorage.setItem(storageKey(workspaceId, module), JSON.stringify(payload));
      }
    },

    restoreFromSession(workspaceId: number): void {
      const tabsByModule: Record<string, TabItem[]> = {};
      const activeTabByModule: Record<string, string | null> = {};

      for (let i = 0; i < sessionStorage.length; i++) {
        const key = sessionStorage.key(i);
        if (!key?.startsWith(`dp:tabs:${workspaceId}:`)) continue;

        const module = key.split(':').pop()!;
        try {
          const data = JSON.parse(sessionStorage.getItem(key)!) as {
            tabs: TabItem[];
            activeTab: string | null;
          };
          tabsByModule[module] = data.tabs;
          activeTabByModule[module] = data.activeTab;
        } catch {
          /* corrupted entry — skip */
        }
      }
      patchState(store, { tabsByModule, activeTabByModule });
    },

    clearAll(): void {
      patchState(store, { tabsByModule: {}, activeTabByModule: {} });
    },
  })),
);
