import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'dp-loading-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (type() === 'text') {
      <div class="flex flex-col gap-2">
        @for (line of lineArray(); track $index) {
          <div
            class="dp-shimmer h-4 rounded-[var(--radius-sm)]"
            [style.width]="lineWidth($index)"
          ></div>
        }
      </div>
    }
    @if (type() === 'card') {
      <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] p-4">
        <div class="dp-shimmer mb-3 h-4 w-1/3 rounded-[var(--radius-sm)]"></div>
        <div class="dp-shimmer mb-2 h-8 w-2/3 rounded-[var(--radius-sm)]"></div>
        <div class="dp-shimmer h-3 w-1/4 rounded-[var(--radius-sm)]"></div>
      </div>
    }
    @if (type() === 'table-row') {
      <div class="flex flex-col gap-1">
        @for (line of lineArray(); track $index) {
          <div class="flex gap-3 py-2">
            <div class="dp-shimmer h-4 w-16 rounded-[var(--radius-sm)]"></div>
            <div class="dp-shimmer h-4 flex-1 rounded-[var(--radius-sm)]"></div>
            <div class="dp-shimmer h-4 w-24 rounded-[var(--radius-sm)]"></div>
            <div class="dp-shimmer h-4 w-20 rounded-[var(--radius-sm)]"></div>
          </div>
        }
      </div>
    }
  `,
})
export class LoadingSkeletonComponent {
  readonly lines = input(3);
  readonly type = input<'text' | 'card' | 'table-row'>('text');

  protected readonly lineArray = computed(() =>
    Array.from({ length: this.lines() }),
  );

  protected lineWidth(index: number): string {
    const total = this.lines();
    if (total <= 1) return '100%';
    if (index === total - 1) return '60%';
    if (index === 0) return '100%';
    return `${90 + (index % 2) * 10}%`;
  }
}
