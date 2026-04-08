import { DestroyRef, inject, WritableSignal } from '@angular/core';

/**
 * Creates a debounced input handler that updates a signal after a delay.
 * Must be called from an injection context (constructor or field initializer).
 *
 * @param onChanged - optional callback invoked after the signal is set (e.g. to reset page)
 *
 * Usage in template: `(input)="onSearchInput($event)"`
 */
export function createDebouncedSearch(
  target: WritableSignal<string>,
  delayMs = 300,
  onChanged?: () => void,
): (event: Event) => void {
  let timer: ReturnType<typeof setTimeout> | null = null;

  inject(DestroyRef).onDestroy(() => {
    if (timer) clearTimeout(timer);
  });

  return (event: Event) => {
    const value = (event.target as HTMLInputElement).value;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      target.set(value);
      onChanged?.();
    }, delayMs);
  };
}
