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
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, AlertTriangle } from 'lucide-angular';

import { OfferSummary } from '@core/models';
import { GridStore } from '@shared/stores/grid.store';

export type FormulaType =
  | 'INCREASE_PCT'
  | 'DECREASE_PCT'
  | 'MULTIPLY'
  | 'FIXED'
  | 'MARKUP_COST'
  | 'ROUND';

export type RoundDirection = 'FLOOR' | 'NEAREST' | 'CEIL';

interface FormulaOption {
  value: FormulaType;
  labelKey: string;
}

interface RoundDirectionOption {
  value: RoundDirection;
  labelKey: string;
}

interface PreviewResult {
  eligibleCount: number;
  avgChangePct: number;
  minPrice: number;
  maxPrice: number;
  minMarginPct: number | null;
  entries: { offerId: number; newPrice: number; originalPrice: number; costPrice: number | null }[];
}

interface BlockedInfo {
  total: number;
  manualLockCount: number;
  promoCount: number;
  noCostPriceCount: number;
}

const INPUT_CLASS =
  'w-full rounded-[var(--radius-md)] border border-[var(--border-default)] ' +
  'bg-[var(--bg-primary)] px-3 py-2 font-mono text-[length:var(--text-sm)] ' +
  'text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]';

const SELECT_CLASS =
  'w-full rounded-[var(--radius-md)] border border-[var(--border-default)] ' +
  'bg-[var(--bg-primary)] px-3 py-2 text-[length:var(--text-sm)] ' +
  'text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]';

function roundPrice(price: number, step: number, direction: RoundDirection): number {
  if (step <= 0) return price;
  switch (direction) {
    case 'FLOOR': return Math.floor(price / step) * step;
    case 'CEIL': return Math.ceil(price / step) * step;
    case 'NEAREST': return Math.round(price / step) * step;
  }
}

@Component({
  selector: 'dp-formula-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, TranslatePipe, LucideAngularModule],
  template: `
    <div class="w-[420px] rounded-[var(--radius-lg)] border border-[var(--border-default)]
                bg-[var(--bg-primary)] p-5 shadow-[var(--shadow-lg)]"
         (click)="$event.stopPropagation()">

      <h3 class="mb-4 text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
        {{ 'grid.formula.title' | translate }}
      </h3>

      <!-- Formula type -->
      <label class="mb-3 block">
        <span class="mb-1 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
          {{ 'grid.formula.action_label' | translate }}
        </span>
        <select [ngModel]="selectedFormula()"
                (ngModelChange)="selectedFormula.set($event)"
                [class]="selectClass">
          @for (opt of formulaOptions; track opt.value) {
            <option [value]="opt.value">{{ opt.labelKey | translate }}</option>
          }
        </select>
      </label>

      <!-- Value input (all except ROUND) -->
      @if (selectedFormula() !== 'ROUND') {
        <label class="mb-3 block">
          <span class="mb-1 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
            {{ 'grid.formula.value_label' | translate }}
          </span>
          <div class="flex items-center gap-2">
            <input type="number"
                   [ngModel]="formulaValue()"
                   (ngModelChange)="formulaValue.set($event)"
                   [min]="0.01"
                   [step]="selectedFormula() === 'MULTIPLY' ? 0.01 : 1"
                   [class]="inputClass" />
            <span class="shrink-0 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ valueSuffix() }}
            </span>
          </div>
        </label>
      }

      <!-- ROUND: step + direction as main params -->
      @if (selectedFormula() === 'ROUND') {
        <div class="mb-3 flex items-center gap-3">
          <label class="flex-1">
            <span class="mb-1 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
              {{ 'grid.formula.round_step' | translate }}
            </span>
            <input type="number" [ngModel]="roundingStep()" (ngModelChange)="roundingStep.set($event)"
                   [min]="1" [step]="1" [class]="inputClass" />
          </label>
          <label class="flex-1">
            <span class="mb-1 block text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
              {{ 'grid.formula.round_direction' | translate }}
            </span>
            <select [ngModel]="roundingDirection()" (ngModelChange)="roundingDirection.set($event)"
                    [class]="selectClass">
              @for (d of roundDirectionOptions; track d.value) {
                <option [value]="d.value">{{ d.labelKey | translate }}</option>
              }
            </select>
          </label>
        </div>
      } @else {
        <!-- Optional rounding for non-ROUND formulas -->
        <div class="mb-3">
          <label class="flex cursor-pointer items-center gap-2">
            <input type="checkbox" [ngModel]="enableRounding()" (ngModelChange)="enableRounding.set($event)"
                   class="accent-[var(--accent-primary)]" />
            <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">
              {{ 'grid.formula.rounding_label' | translate }}
            </span>
          </label>
          @if (enableRounding()) {
            <div class="mt-2 flex items-center gap-2 pl-6">
              <input type="number" [ngModel]="roundingStep()" (ngModelChange)="roundingStep.set($event)"
                     [min]="1" [step]="1"
                     class="w-20 rounded-[var(--radius-md)] border border-[var(--border-default)]
                            bg-[var(--bg-primary)] px-2 py-1.5 font-mono text-[length:var(--text-sm)]
                            text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]" />
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">₽</span>
              <select [ngModel]="roundingDirection()" (ngModelChange)="roundingDirection.set($event)"
                      class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                             bg-[var(--bg-primary)] px-2 py-1.5 text-[length:var(--text-sm)]
                             text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]">
                @for (d of roundDirectionOptions; track d.value) {
                  <option [value]="d.value">{{ d.labelKey | translate }}</option>
                }
              </select>
            </div>
          }
        </div>
      }

      <div class="my-4 border-t border-[var(--border-default)]"></div>

      <!-- Preview -->
      @if (preview(); as p) {
        <div class="mb-4">
          <h4 class="mb-2 text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]">
            {{ 'grid.formula.preview_title' | translate:{ count: p.eligibleCount } }}
          </h4>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1.5 text-[length:var(--text-sm)]">
            <span class="text-[var(--text-secondary)]">{{ 'grid.formula.preview_avg_change' | translate }}</span>
            <span class="text-right font-mono font-medium"
                  [class]="p.avgChangePct >= 0
                    ? 'text-[var(--finance-positive)]'
                    : 'text-[var(--finance-negative)]'">
              {{ p.avgChangePct >= 0 ? '+' : '' }}{{ formatPct(p.avgChangePct) }}
            </span>
            <span class="text-[var(--text-secondary)]">{{ 'grid.formula.preview_min_price' | translate }}</span>
            <span class="text-right font-mono">{{ formatMoney(p.minPrice) }}</span>
            <span class="text-[var(--text-secondary)]">{{ 'grid.formula.preview_max_price' | translate }}</span>
            <span class="text-right font-mono">{{ formatMoney(p.maxPrice) }}</span>
            @if (p.minMarginPct !== null) {
              <span class="text-[var(--text-secondary)]">{{ 'grid.formula.preview_min_margin' | translate }}</span>
              <span class="text-right font-mono"
                    [class]="p.minMarginPct < 10
                      ? 'text-[var(--status-warning)]'
                      : 'text-[var(--text-primary)]'">
                {{ formatPct(p.minMarginPct) }}
              </span>
            }
          </div>
        </div>
      }

      <!-- Blocked -->
      @if (blocked().total > 0) {
        <div class="my-4 border-t border-[var(--border-default)]"></div>
        <div class="mb-4">
          <div class="flex items-center gap-1.5 text-[length:var(--text-sm)] font-medium text-[var(--status-warning)]">
            <lucide-icon [img]="alertTriangleIcon" [size]="14" />
            {{ 'grid.formula.blocked_title' | translate:{ count: blocked().total } }}
          </div>
          <ul class="mt-1.5 list-disc space-y-0.5 pl-5 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            @if (blocked().manualLockCount > 0) {
              <li>{{ blocked().manualLockCount }} — {{ 'grid.formula.blocked_manual_lock' | translate }}</li>
            }
            @if (blocked().promoCount > 0) {
              <li>{{ blocked().promoCount }} — {{ 'grid.formula.blocked_promo' | translate }}</li>
            }
            @if (blocked().noCostPriceCount > 0) {
              <li>{{ blocked().noCostPriceCount }} — {{ 'grid.formula.blocked_no_cost' | translate }}</li>
            }
          </ul>
        </div>
      }

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 pt-2">
        <button (click)="close.emit()"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)]
                       px-4 py-2 text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]
                       transition-colors hover:bg-[var(--bg-tertiary)]">
          {{ 'common.cancel' | translate }}
        </button>
        <button (click)="onApply()"
                [disabled]="!canApply()"
                class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)]
                       px-4 py-2 text-[length:var(--text-sm)] font-medium text-white
                       transition-colors hover:bg-[var(--accent-primary-hover)]
                       disabled:cursor-not-allowed disabled:opacity-50">
          {{ 'grid.formula.apply_btn' | translate:{ count: preview()?.eligibleCount ?? 0 } }}
        </button>
      </div>
    </div>
  `,
})
export class FormulaPanelComponent {

  readonly offers = input.required<OfferSummary[]>();
  readonly applied = output<void>();
  readonly close = output<void>();

  private readonly gridStore = inject(GridStore);

  readonly alertTriangleIcon = AlertTriangle;
  readonly inputClass = INPUT_CLASS;
  readonly selectClass = SELECT_CLASS;

  readonly formulaOptions: FormulaOption[] = [
    { value: 'INCREASE_PCT', labelKey: 'grid.formula.type.increase_pct' },
    { value: 'DECREASE_PCT', labelKey: 'grid.formula.type.decrease_pct' },
    { value: 'MULTIPLY', labelKey: 'grid.formula.type.multiply' },
    { value: 'FIXED', labelKey: 'grid.formula.type.fixed' },
    { value: 'MARKUP_COST', labelKey: 'grid.formula.type.markup_cost' },
    { value: 'ROUND', labelKey: 'grid.formula.type.round' },
  ];

  readonly roundDirectionOptions: RoundDirectionOption[] = [
    { value: 'FLOOR', labelKey: 'grid.formula.direction.floor' },
    { value: 'NEAREST', labelKey: 'grid.formula.direction.nearest' },
    { value: 'CEIL', labelKey: 'grid.formula.direction.ceil' },
  ];

  readonly selectedFormula = signal<FormulaType>('INCREASE_PCT');
  readonly formulaValue = signal<number | null>(null);
  readonly enableRounding = signal(false);
  readonly roundingStep = signal<number | null>(10);
  readonly roundingDirection = signal<RoundDirection>('FLOOR');

  readonly valueSuffix = computed(() => {
    switch (this.selectedFormula()) {
      case 'INCREASE_PCT':
      case 'DECREASE_PCT':
      case 'MARKUP_COST':
        return '%';
      case 'MULTIPLY':
        return '×';
      case 'FIXED':
        return '₽';
      default:
        return '';
    }
  });

  readonly blocked = computed<BlockedInfo>(() => {
    const offers = this.offers();
    const formula = this.selectedFormula();
    let manualLockCount = 0;
    let promoCount = 0;
    let noCostPriceCount = 0;

    for (const o of offers) {
      if (o.manualLock) {
        manualLockCount++;
      } else if (o.promoStatus === 'PARTICIPATING') {
        promoCount++;
      } else if (formula === 'MARKUP_COST' && !o.costPrice) {
        noCostPriceCount++;
      }
    }

    return {
      total: manualLockCount + promoCount + noCostPriceCount,
      manualLockCount,
      promoCount,
      noCostPriceCount,
    };
  });

  readonly preview = computed<PreviewResult | null>(() => {
    const formula = this.selectedFormula();
    const value = this.formulaValue();
    const step = this.roundingStep();
    const direction = this.roundingDirection();
    const applyRounding = this.enableRounding();

    if (formula === 'ROUND') {
      if (!step || step <= 0) return null;
    } else {
      if (value === null || value <= 0) return null;
    }

    const offers = this.offers();
    const entries: PreviewResult['entries'] = [];
    const changePcts: number[] = [];
    const margins: number[] = [];
    let minPrice = Infinity;
    let maxPrice = -Infinity;

    for (const o of offers) {
      if (o.manualLock || o.promoStatus === 'PARTICIPATING') continue;
      if (formula === 'MARKUP_COST' && !o.costPrice) continue;

      const currentPrice = o.currentPrice ?? 0;
      if (currentPrice <= 0 && formula !== 'FIXED') continue;

      const costPrice = o.costPrice ?? 0;
      let newPrice = this.computeNewPrice(currentPrice, costPrice, formula, value!, step!, direction);

      if (applyRounding && formula !== 'ROUND' && step && step > 0) {
        newPrice = roundPrice(newPrice, step, direction);
      }

      newPrice = Math.max(0.01, Math.round(newPrice * 100) / 100);

      const changePct = currentPrice > 0
        ? ((newPrice - currentPrice) / currentPrice) * 100
        : 0;
      const marginPct = newPrice > 0 && costPrice > 0
        ? ((newPrice - costPrice) / newPrice) * 100
        : null;

      entries.push({ offerId: o.offerId, newPrice, originalPrice: currentPrice, costPrice: costPrice > 0 ? costPrice : null });
      changePcts.push(changePct);
      if (marginPct !== null) margins.push(marginPct);
      if (newPrice < minPrice) minPrice = newPrice;
      if (newPrice > maxPrice) maxPrice = newPrice;
    }

    if (entries.length === 0) {
      return { eligibleCount: 0, avgChangePct: 0, minPrice: 0, maxPrice: 0, minMarginPct: null, entries: [] };
    }

    const avgChangePct = changePcts.reduce((s, v) => s + v, 0) / changePcts.length;
    const minMarginPct = margins.length > 0 ? Math.min(...margins) : null;

    return { eligibleCount: entries.length, avgChangePct, minPrice, maxPrice, minMarginPct, entries };
  });

  readonly canApply = computed(() => {
    const p = this.preview();
    return !!p && p.eligibleCount > 0;
  });

  formatPct(value: number): string {
    return value.toFixed(1).replace('.', ',') + '%';
  }

  formatMoney(value: number): string {
    const intPart = Math.floor(Math.abs(value)).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
    return `${intPart}\u00A0₽`;
  }

  onApply(): void {
    const p = this.preview();
    if (!p || p.eligibleCount === 0) return;

    if (!this.gridStore.draftMode()) {
      this.gridStore.setDraftMode(true);
    }

    for (const entry of p.entries) {
      this.gridStore.setDraftPrice(entry.offerId, entry.newPrice, entry.originalPrice, entry.costPrice);
    }

    this.applied.emit();
    this.close.emit();
  }

  private computeNewPrice(
      currentPrice: number,
      costPrice: number,
      formula: FormulaType,
      value: number,
      step: number,
      direction: RoundDirection): number {
    switch (formula) {
      case 'INCREASE_PCT': return currentPrice * (1 + value / 100);
      case 'DECREASE_PCT': return currentPrice * (1 - value / 100);
      case 'MULTIPLY': return currentPrice * value;
      case 'FIXED': return value;
      case 'MARKUP_COST': return costPrice * (1 + value / 100);
      case 'ROUND': return roundPrice(currentPrice, step, direction);
    }
  }
}
