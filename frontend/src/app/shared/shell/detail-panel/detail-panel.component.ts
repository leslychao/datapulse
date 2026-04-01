import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { LucideAngularModule, X, GripVertical } from 'lucide-angular';

import { DetailPanelService } from '@shared/services/detail-panel.service';

@Component({
  selector: 'dp-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `
    <div class="relative flex h-full flex-col bg-[var(--bg-primary)]"
         [style.width.px]="panelService.width()">
      <!-- Resize handle -->
      <div #resizeHandle
           class="absolute left-0 top-0 z-10 h-full w-1 cursor-col-resize
                  transition-colors duration-[var(--transition-fast)]
                  hover:bg-[var(--accent-primary)]"
           [class.bg-[var(--accent-primary)]]="isResizing()"
           (mousedown)="startResize($event)">
      </div>

      <!-- Content (child provides its own header) -->
      <div class="flex-1 overflow-auto">
        <ng-content />
      </div>
    </div>
  `,
})
export class DetailPanelComponent {
  protected readonly panelService = inject(DetailPanelService);

  readonly closeIcon = X;
  readonly gripIcon = GripVertical;
  readonly isResizing = signal(false);

  private startX = 0;
  private startWidth = 0;

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.panelService.close();
  }

  startResize(event: MouseEvent): void {
    event.preventDefault();
    this.isResizing.set(true);
    this.startX = event.clientX;
    this.startWidth = this.panelService.width();

    const onMouseMove = (e: MouseEvent) => {
      const delta = this.startX - e.clientX;
      this.panelService.resize(this.startWidth + delta);
    };

    const onMouseUp = () => {
      this.isResizing.set(false);
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }
}
