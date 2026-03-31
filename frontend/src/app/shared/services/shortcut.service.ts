import { Injectable, inject, NgZone } from '@angular/core';

interface ShortcutEntry {
  shortcut: string;
  handler: () => void;
  scope: string;
}

const PREVENT_DEFAULT_SHORTCUTS = new Set(['ctrl+k', 'ctrl+s', 'ctrl+f', 'ctrl+e']);

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA') return true;
  return target.isContentEditable;
}

function normalizeKeyEvent(e: KeyboardEvent): string {
  const parts: string[] = [];
  if (e.ctrlKey || e.metaKey) parts.push('ctrl');
  if (e.shiftKey) parts.push('shift');
  if (e.altKey) parts.push('alt');
  parts.push(e.key.toLowerCase());
  return parts.join('+');
}

@Injectable({ providedIn: 'root' })
export class ShortcutService {
  private readonly zone = inject(NgZone);
  private readonly entries: ShortcutEntry[] = [];
  private initialized = false;

  register(shortcut: string, handler: () => void, scope = 'global'): void {
    this.entries.push({ shortcut: shortcut.toLowerCase(), handler, scope });
  }

  init(): void {
    if (this.initialized) return;
    this.initialized = true;

    this.zone.runOutsideAngular(() => {
      document.addEventListener('keydown', (e) => this.onKeyDown(e));
    });
  }

  private onKeyDown(e: KeyboardEvent): void {
    const combo = normalizeKeyEvent(e);

    if (PREVENT_DEFAULT_SHORTCUTS.has(combo)) {
      e.preventDefault();
    }

    if (isEditableTarget(e.target)) return;

    const match = this.entries.find((entry) => entry.shortcut === combo);
    if (!match) return;

    e.preventDefault();
    this.zone.run(() => match.handler());
  }
}
