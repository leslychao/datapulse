import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';

const STORAGE_KEY_COMPLETED = 'dp-tours-completed';

function loadCompleted(): string[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY_COMPLETED) ?? '[]');
  } catch {
    return [];
  }
}

export interface TourProgressState {
  completedTourIds: string[];
}

const initialState: TourProgressState = {
  completedTourIds: loadCompleted(),
};

export const TourProgressStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    completedCount: computed(() => store.completedTourIds().length),
  })),
  withMethods((store) => ({
    isCompleted(tourId: string): boolean {
      return store.completedTourIds().includes(tourId);
    },
    markCompleted(tourId: string): void {
      if (store.completedTourIds().includes(tourId)) return;
      const updated = [...store.completedTourIds(), tourId];
      patchState(store, { completedTourIds: updated });
      localStorage.setItem(STORAGE_KEY_COMPLETED, JSON.stringify(updated));
    },
    resetAll(): void {
      patchState(store, { completedTourIds: [] });
      localStorage.removeItem(STORAGE_KEY_COMPLETED);
    },
  })),
);
