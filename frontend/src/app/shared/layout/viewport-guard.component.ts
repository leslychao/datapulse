import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

@Component({
  selector: 'dp-viewport-guard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(window:resize)': 'onResize()',
  },
  template: `
    @if (isWideEnough()) {
      <ng-content />
    } @else {
      <div class="flex h-screen items-center justify-center bg-[var(--bg-primary)] px-6">
        <div class="flex max-w-md flex-col items-center gap-4 text-center">
          <span class="text-lg font-semibold text-[var(--text-primary)]">Datapulse</span>
          <p class="text-sm text-[var(--text-secondary)]">
            Datapulse предназначен для экранов 1280px и шире.
          </p>
          <p class="text-sm text-[var(--text-tertiary)]">
            Пожалуйста, используйте компьютер с большим экраном для работы с платформой.
          </p>
        </div>
      </div>
    }
  `,
})
export class ViewportGuardComponent {
  private static readonly MIN_WIDTH = 1280;

  protected readonly isWideEnough = signal(
    typeof window !== 'undefined' ? window.innerWidth >= ViewportGuardComponent.MIN_WIDTH : true,
  );

  protected onResize(): void {
    this.isWideEnough.set(window.innerWidth >= ViewportGuardComponent.MIN_WIDTH);
  }
}
