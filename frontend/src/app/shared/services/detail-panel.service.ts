import { Injectable, signal } from '@angular/core';

const MIN_WIDTH = 320;
const MAX_VIEWPORT_RATIO = 0.5;

@Injectable({ providedIn: 'root' })
export class DetailPanelService {
  readonly isOpen = signal(false);
  readonly width = signal(400);
  readonly currentEntity = signal<unknown>(null);

  open(entity: unknown): void {
    this.currentEntity.set(entity);
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.currentEntity.set(null);
  }

  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.isOpen.set(true);
    }
  }

  resize(newWidth: number): void {
    const maxWidth = Math.floor(window.innerWidth * MAX_VIEWPORT_RATIO);
    this.width.set(Math.min(maxWidth, Math.max(MIN_WIDTH, newWidth)));
  }
}
