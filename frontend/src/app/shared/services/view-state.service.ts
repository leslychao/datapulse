import { inject, Injectable } from '@angular/core';

import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

const STORAGE_VERSION = 1;
const KEY_PREFIX = 'dp:vs';

export interface PersistedColumnState {
  colId: string;
  width?: number;
  hide?: boolean;
  pinned?: string | null;
}

export interface PersistedViewState {
  version: number;
  filters?: Record<string, any>;
  sort?: { column: string; direction: 'asc' | 'desc' };
  columnState?: PersistedColumnState[];
  updatedAt: number;
}

@Injectable({ providedIn: 'root' })
export class ViewStateService {

  private readonly wsStore = inject(WorkspaceContextStore);

  save(pageKey: string, patch: Partial<Omit<PersistedViewState, 'version' | 'updatedAt'>>): void {
    const key = this.buildKey(pageKey);
    if (!key) return;

    const existing = this.readRaw(key);
    const merged: PersistedViewState = {
      ...existing,
      ...patch,
      version: STORAGE_VERSION,
      updatedAt: Date.now(),
    };
    try {
      localStorage.setItem(key, JSON.stringify(merged));
    } catch { /* storage full — ignore */ }
  }

  restore(pageKey: string): PersistedViewState | null {
    const key = this.buildKey(pageKey);
    if (!key) return null;

    const data = this.readRaw(key);
    if (!data || data.version !== STORAGE_VERSION) return null;
    return data;
  }

  restoreFilters(pageKey: string): Record<string, any> | null {
    return this.restore(pageKey)?.filters ?? null;
  }

  restoreSort(pageKey: string): { column: string; direction: 'asc' | 'desc' } | null {
    return this.restore(pageKey)?.sort ?? null;
  }

  restoreColumnState(pageKey: string): PersistedColumnState[] | null {
    return this.restore(pageKey)?.columnState ?? null;
  }

  clearPage(pageKey: string): void {
    const key = this.buildKey(pageKey);
    if (key) localStorage.removeItem(key);
  }

  clearWorkspace(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) return;
    const prefix = `${KEY_PREFIX}:${wsId}:`;
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k?.startsWith(prefix)) keysToRemove.push(k);
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k));
  }

  private buildKey(pageKey: string): string | null {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) return null;
    return `${KEY_PREFIX}:${wsId}:${pageKey}`;
  }

  private readRaw(storageKey: string): PersistedViewState | null {
    try {
      const raw = localStorage.getItem(storageKey);
      if (!raw) return null;
      return JSON.parse(raw) as PersistedViewState;
    } catch {
      return null;
    }
  }
}
