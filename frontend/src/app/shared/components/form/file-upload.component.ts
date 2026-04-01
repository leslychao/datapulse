import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Upload } from 'lucide-angular';

@Component({
  selector: 'dp-file-upload',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule],
  template: `
    <div
      class="flex flex-col items-center gap-3 rounded-[var(--radius-md)] border-2 border-dashed p-6 text-center transition-colors"
      [class]="dragging()
        ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)]'
        : 'border-[var(--border-default)] bg-[var(--bg-secondary)]'"
      (dragover)="onDragOver($event)"
      (dragleave)="dragging.set(false)"
      (drop)="onDrop($event)"
    >
      <lucide-icon [img]="Upload" [size]="24" class="text-[var(--text-tertiary)]" />
      <div class="flex flex-col gap-1">
        <span class="text-sm text-[var(--text-secondary)]">{{ hint() | translate }}</span>
        <label class="cursor-pointer text-sm font-medium text-[var(--accent-primary)] hover:underline">
          {{ 'common.browse' | translate }}
          <input
            type="file"
            class="hidden"
            [accept]="accept()"
            (change)="onFileSelected($event)"
          />
        </label>
      </div>
      @if (fileName()) {
        <span class="text-sm text-[var(--text-primary)]">{{ fileName() }}</span>
      }
    </div>
  `,
})
export class FileUploadComponent {
  protected readonly Upload = Upload;

  readonly accept = input('.csv');
  readonly hint = input('common.drag_or_browse');

  readonly fileSelected = output<File>();

  protected readonly dragging = signal(false);
  protected readonly fileName = signal('');

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files[0];
    if (file) {
      this.fileName.set(file.name);
      this.fileSelected.emit(file);
    }
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.fileName.set(file.name);
      this.fileSelected.emit(file);
    }
  }
}
