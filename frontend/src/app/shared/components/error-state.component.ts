import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, AlertTriangle } from 'lucide-angular';

@Component({
  selector: 'dp-error-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule],
  template: `
    <div class="flex flex-col items-center gap-3 py-12 text-center">
      <div class="flex h-12 w-12 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--status-error)_10%,transparent)]">
        <lucide-icon [img]="AlertTriangle" [size]="24" class="text-[var(--status-error)]" />
      </div>
      <p class="text-sm font-medium text-[var(--text-primary)]">{{ title() | translate }}</p>
      @if (message()) {
        <p class="max-w-sm text-sm text-[var(--text-secondary)]">{{ message() | translate }}</p>
      }
      @if (retryLabel()) {
        <button
          (click)="retry.emit()"
          class="mt-2 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          {{ retryLabel() | translate }}
        </button>
      }
    </div>
  `,
})
export class ErrorStateComponent {
  protected readonly AlertTriangle = AlertTriangle;

  readonly title = input('common.error');
  readonly message = input('');
  readonly retryLabel = input('');
  readonly retry = output<void>();
}
