import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, AlertTriangle } from 'lucide-angular';
import { formatDate } from '@angular/common';

import { OfferSummary, CostUpdateOperation, BulkFormulaCostRequest } from '@core/models';
import { CostProfileApiService } from '@core/api/cost-profile-api.service';
import { ToastService } from '@shared/shell/toast/toast.service';

interface OperationOption {
  value: CostUpdateOperation;
  labelKey: string;
}

const INPUT_CLASS =
  'w-full rounded-[var(--radius-md)] border border-[var(--border-default)] ' +
  'bg-[var(--bg-primary)] px-3 py-2 font-mono text-[length:var(--text-sm)] ' +
  'text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]';

@Component({
  selector: 'dp-cost-update-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, TranslatePipe, LucideAngularModule],
  template: `
    <div class="w-[420px] rounded-[var(--radius-lg)] border border-[var(--border-default)]
                bg-[var(--bg-primary)] p-5 shadow-[var(--shadow-lg)]"
         (click)="$event.stopPropagation()">

      <h3 class="mb-4 text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
        {{ 'grid.cost.title' | translate }}
      </h3>

      <!-- Operation type (radio buttons) -->
      <fieldset class="mb-4">
        <legend class="mb-2 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
          {{ 'grid.cost.operation_label' | translate }}
        </legend>
        @for (opt of operationOptions; track opt.value) {
          <label class="flex cursor-pointer items-center gap-2 py-1">
            <input type="radio"
                   name="costOperation"
                   [value]="opt.value"
                   [checked]="selectedOperation() === opt.value"
                   (change)="selectedOperation.set(opt.value)"
                   class="accent-[var(--accent-primary)]" />
            <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">
              {{ opt.labelKey | translate }}
            </span>
          </label>
        }
      </fieldset>

      <!-- Value input -->
      <label class="mb-4 block">
        <span class="mb-1 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
          {{ 'grid.cost.value_label' | translate }}
        </span>
        <div class="flex items-center gap-2">
          <input type="number"
                 [ngModel]="value()"
                 (ngModelChange)="value.set($event)"
                 [min]="0.01"
                 [step]="selectedOperation() === 'MULTIPLY' ? 0.01 : 1"
                 [class]="inputClass" />
          <span class="shrink-0 text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
            {{ valueSuffix() }}
          </span>
        </div>
      </label>

      <!-- Valid from date picker -->
      <label class="mb-4 block">
        <span class="mb-1 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
          {{ 'grid.cost.valid_from_label' | translate }}
        </span>
        <input type="date"
               [ngModel]="validFrom()"
               (ngModelChange)="validFrom.set($event)"
               [class]="inputClass" />
      </label>

      <div class="my-4 border-t border-[var(--border-default)]"></div>

      <!-- Info section -->
      <div class="mb-4 space-y-1.5 text-[length:var(--text-sm)]">
        <p class="text-[var(--text-primary)]">
          {{ 'grid.cost.will_update' | translate:{ count: totalCount() } }}
        </p>
        @if (noCostCount() > 0) {
          <div class="flex items-start gap-1.5 text-[var(--status-warning)]">
            <lucide-icon [img]="alertTriangleIcon" [size]="14" class="mt-0.5 shrink-0" />
            <span>{{ 'grid.cost.no_cost_warning' | translate:{ count: noCostCount() } }}</span>
          </div>
        }
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 pt-2">
        <button (click)="close.emit()"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)]
                       px-4 py-2 text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]
                       transition-colors hover:bg-[var(--bg-tertiary)]">
          {{ 'common.cancel' | translate }}
        </button>
        <button (click)="onSubmit()"
                [disabled]="!canSubmit() || bulkFormulaMutation.isPending()"
                class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)]
                       px-4 py-2 text-[length:var(--text-sm)] font-medium text-white
                       transition-colors hover:bg-[var(--accent-primary-hover)]
                       disabled:cursor-not-allowed disabled:opacity-50">
          @if (bulkFormulaMutation.isPending()) {
            {{ 'common.loading' | translate }}
          } @else {
            {{ 'grid.cost.submit_btn' | translate:{ count: totalCount() } }}
          }
        </button>
      </div>
    </div>
  `,
})
export class CostUpdatePanelComponent {

  readonly offers = input.required<OfferSummary[]>();
  readonly applied = output<void>();
  readonly close = output<void>();

  private readonly costApi = inject(CostProfileApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly alertTriangleIcon = AlertTriangle;
  readonly inputClass = INPUT_CLASS;

  readonly operationOptions: OperationOption[] = [
    { value: 'FIXED', labelKey: 'grid.cost.op.fixed' },
    { value: 'INCREASE_PCT', labelKey: 'grid.cost.op.increase_pct' },
    { value: 'DECREASE_PCT', labelKey: 'grid.cost.op.decrease_pct' },
    { value: 'MULTIPLY', labelKey: 'grid.cost.op.multiply' },
  ];

  readonly selectedOperation = signal<CostUpdateOperation>('FIXED');
  readonly value = signal<number | null>(null);
  readonly validFrom = signal<string>(formatDate(new Date(), 'yyyy-MM-dd', 'en'));

  readonly valueSuffix = computed(() => {
    switch (this.selectedOperation()) {
      case 'FIXED': return '₽';
      case 'INCREASE_PCT':
      case 'DECREASE_PCT': return '%';
      case 'MULTIPLY': return '×';
    }
  });

  readonly totalCount = computed(() => this.offers().length);

  readonly noCostCount = computed(() =>
    this.offers().filter(o => o.costPrice == null || o.costPrice === 0).length,
  );

  readonly canSubmit = computed(() => {
    const v = this.value();
    return v !== null && v > 0 && this.validFrom().length > 0 && this.totalCount() > 0;
  });

  readonly bulkFormulaMutation = injectMutation(() => ({
    mutationFn: (req: BulkFormulaCostRequest) =>
      lastValueFrom(this.costApi.bulkFormula(req)),
    onSuccess: (res) => {
      const failed = res.skipped + res.errors.length;
      if (failed > 0) {
        this.toast.warning(this.translate.instant('grid.cost.update_partial', {
          updated: res.updated, skipped: res.skipped,
        }));
      } else {
        this.toast.success(this.translate.instant('grid.cost.update_success', {
          count: res.updated,
        }));
      }
      this.applied.emit();
      this.close.emit();
    },
    onError: () => {
      this.toast.error(this.translate.instant('grid.cost.update_error'));
    },
  }));

  onSubmit(): void {
    const v = this.value();
    if (v === null || v <= 0) return;

    const sellerSkuIds = this.offers().map(o => o.offerId);

    this.bulkFormulaMutation.mutate({
      sellerSkuIds,
      operation: this.selectedOperation(),
      value: v,
      validFrom: this.validFrom(),
    });
  }
}
