import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';

export interface WorkspaceContextState {
  currentWorkspaceId: number | null;
  currentWorkspaceName: string | null;
  loading: boolean;
}

const initialState: WorkspaceContextState = {
  currentWorkspaceId: null,
  currentWorkspaceName: null,
  loading: false,
};

export const WorkspaceContextStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    hasWorkspace: computed(() => store.currentWorkspaceId() !== null),
  })),
  withMethods((store) => ({
    setWorkspace(id: number, name: string): void {
      patchState(store, { currentWorkspaceId: id, currentWorkspaceName: name, loading: false });
      localStorage.setItem('dp_last_workspace_id', String(id));
    },
    clearWorkspace(): void {
      patchState(store, { currentWorkspaceId: null, currentWorkspaceName: null, loading: false });
    },
    setLoading(loading: boolean): void {
      patchState(store, { loading });
    },
  })),
);
