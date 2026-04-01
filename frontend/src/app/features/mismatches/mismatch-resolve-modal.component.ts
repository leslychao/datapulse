import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

import { MismatchResolution } from '@core/models';

const RESOLUTION_OPTIONS: MismatchResolution[] = [
  'ACCEPTED',
  'REPRICED',
  'INVESTIGATED',
  'EXTERNAL',
  'IGNORED',
];

@Component({
  selector: 'dp-mismatch-resolve-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, TranslatePipe],
  template: `
    @if (open()) {
      <div class="fixed inset-0 z-[9000] flex items-center justify-center">
        <div class="absolute inset-0 bg-black/40" (click)="onCancel()"></div>
        <div
          class="relative z-10 w-full max-w-md rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6 shadow-[var(--shadow-md)]"
        >
          <h3 class="text-base font-semibold text-[var(--text-primary)]">
            {{ 'mismatches.resolve.title' | translate }}
          </h3>

          <div class="mt-4 flex flex-col gap-1.5">
            <label class="text-sm text-[var(--text-secondary)]" for="res">
              {{ 'mismatches.resolve.resolution_label' | translate }}
            </label>
            <select
              id="res"
              [(ngModel)]="resolutionModel"
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            >
              @for (opt of resolutionOptions; track opt) {
                <option [value]="opt">{{ 'mismatches.resolution.' + opt | translate }}</option>
              }
            </select>
          </div>

          <div class="mt-4 flex flex-col gap-1.5">
            <label class="text-sm text-[var(--text-secondary)]" for="note">
              {{ 'mismatches.resolve.note_label' | translate }}
            </label>
            <textarea
              id="note"
              [(ngModel)]="noteModel"
              rows="4"
              required
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            ></textarea>
          </div>

          <div class="mt-6 flex justify-end gap-3">
            <button
              type="button"
              (click)="onCancel()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'actions.cancel' | translate }}
            </button>
            <button
              type="button"
              (click)="onResolve()"
              [disabled]="!canSubmit()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ 'mismatches.resolve.confirm' | translate }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class MismatchResolveModalComponent {
  readonly open = input(false);

  readonly resolved = output<{ resolution: MismatchResolution; note: string }>();
  readonly cancelled = output<void>();

  protected readonly resolutionOptions = RESOLUTION_OPTIONS;

  protected resolutionModel: MismatchResolution = 'ACCEPTED';
  protected noteModel = '';

  protected canSubmit(): boolean {
    return this.noteModel.trim().length > 0;
  }

  protected onResolve(): void {
    if (!this.canSubmit()) return;
    this.resolved.emit({
      resolution: this.resolutionModel,
      note: this.noteModel.trim(),
    });
    this.reset();
  }

  protected onCancel(): void {
    this.cancelled.emit();
    this.reset();
  }

  private reset(): void {
    this.resolutionModel = 'ACCEPTED';
    this.noteModel = '';
  }
}
