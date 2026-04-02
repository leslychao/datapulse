import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-pagination-bar',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center justify-between border-t border-[var(--border-default)] px-4 py-2 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
      <span>{{ 'pagination.showing' | translate:{ from: from(), to: to(), total: formattedTotal() } }}</span>

      <div class="flex items-center gap-2">
        <button
          [disabled]="!canPrev()"
          (click)="goToPrev()"
          class="cursor-pointer rounded-[var(--radius-sm)] p-1 transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-30"
          [attr.aria-label]="'pagination.prev' | translate"
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>
        <span class="font-mono text-[length:var(--text-sm)]">{{ currentPage() + 1 }} / {{ totalPages() }}</span>
        <button
          [disabled]="!canNext()"
          (click)="goToNext()"
          class="cursor-pointer rounded-[var(--radius-sm)] p-1 transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-30"
          [attr.aria-label]="'pagination.next' | translate"
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="m9 18 6-6-6-6" />
          </svg>
        </button>
      </div>

      <div class="flex items-center gap-2">
        <span>{{ 'pagination.rows_per_page' | translate }}</span>
        <select
          (change)="onPageSizeChange($event)"
          class="h-7 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-[length:var(--text-sm)] text-[var(--text-primary)]"
        >
          @for (size of pageSizeOptions(); track size) {
            <option [value]="size" [selected]="size === pageSize()">{{ size }}</option>
          }
        </select>
      </div>
    </div>
  `,
})
export class PaginationBarComponent {
  readonly totalItems = input(0);
  readonly pageSize = input(25);
  readonly currentPage = input(0);
  readonly pageSizeOptions = input<number[]>([25, 50, 100]);

  readonly pageChange = output<{ page: number; pageSize: number }>();

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalItems() / this.pageSize())),
  );

  protected readonly from = computed(() =>
    this.totalItems() === 0 ? 0 : this.currentPage() * this.pageSize() + 1,
  );

  protected readonly to = computed(() =>
    Math.min((this.currentPage() + 1) * this.pageSize(), this.totalItems()),
  );

  protected readonly canPrev = computed(() => this.currentPage() > 0);

  protected readonly canNext = computed(() =>
    this.currentPage() < this.totalPages() - 1,
  );

  protected readonly formattedTotal = computed(() =>
    this.totalItems().toLocaleString('ru-RU'),
  );

  goToPrev(): void {
    if (!this.canPrev()) return;
    this.pageChange.emit({
      page: this.currentPage() - 1,
      pageSize: this.pageSize(),
    });
  }

  goToNext(): void {
    if (!this.canNext()) return;
    this.pageChange.emit({
      page: this.currentPage() + 1,
      pageSize: this.pageSize(),
    });
  }

  onPageSizeChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.pageChange.emit({
      page: 0,
      pageSize: Number(select.value),
    });
  }
}
