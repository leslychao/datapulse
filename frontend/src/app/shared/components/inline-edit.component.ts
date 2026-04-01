import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'dp-inline-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    @if (editing()) {
      <input
        #editInput
        type="text"
        [ngModel]="editValue()"
        (ngModelChange)="editValue.set($event)"
        (blur)="onSave()"
        (keydown.enter)="onSave()"
        (keydown.escape)="onCancel()"
        class="h-7 w-full rounded-[var(--radius-sm)] border border-[var(--accent-primary)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none"
      />
    } @else {
      <span
        (dblclick)="startEdit()"
        class="cursor-pointer rounded px-1 py-0.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-secondary)]"
        [title]="'Double-click to edit'"
      >{{ value() || '\u2014' }}</span>
    }
  `,
})
export class InlineEditComponent {
  readonly value = input('');
  readonly saved = output<string>();

  protected readonly editing = signal(false);
  protected readonly editValue = signal('');

  protected startEdit(): void {
    this.editValue.set(this.value());
    this.editing.set(true);
  }

  protected onSave(): void {
    const newVal = this.editValue().trim();
    if (newVal !== this.value()) {
      this.saved.emit(newVal);
    }
    this.editing.set(false);
  }

  protected onCancel(): void {
    this.editing.set(false);
  }
}
