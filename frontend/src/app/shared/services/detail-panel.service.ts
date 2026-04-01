import { Injectable, signal } from '@angular/core';

export type DetailPanelEntityType = 'connection' | 'offer' | 'alert';

const MIN_WIDTH = 320;
const MAX_VIEWPORT_RATIO = 0.5;

@Injectable({ providedIn: 'root' })
export class DetailPanelService {
  readonly isOpen = signal(false);
  readonly width = signal(400);
  readonly entityType = signal<DetailPanelEntityType | null>(null);
  readonly entityId = signal<number | null>(null);

  open(type: DetailPanelEntityType, id: number): void {
    this.entityType.set(type);
    this.entityId.set(id);
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.entityType.set(null);
    this.entityId.set(null);
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
