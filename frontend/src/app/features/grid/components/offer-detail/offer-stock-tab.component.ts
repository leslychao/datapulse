import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { OfferDetail } from '@core/models';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MoneyDisplayComponent } from '@shared/components/money-display.component';

@Component({
  selector: 'dp-offer-stock-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, StatusBadgeComponent, MoneyDisplayComponent],
  template: `
    <div class="p-4">
      <!-- Сводка -->
      <section class="mb-5">
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'detail.stock.summary' | translate }}
        </h4>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'detail.stock.total_available' | translate }}
          </span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
            {{ offer().availableStock ?? '—' }} {{ 'detail.stock.units' | translate }}
          </span>

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'detail.stock.days_cover' | translate }}
          </span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
            @if (offer().daysOfCover !== null) {
              {{ offer().daysOfCover!.toFixed(1).replace('.', ',') }}
            } @else {
              —
            }
          </span>

          @if (offer().stockRisk) {
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'detail.stock.risk' | translate }}
            </span>
            <dp-status-badge
              [label]="'grid.stock_risk.' + offer().stockRisk | translate"
              [color]="riskColor(offer().stockRisk!)"
            />
          }
        </div>
      </section>

      <!-- По складам -->
      @if (offer().warehouses.length) {
        <section class="mb-5">
          <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
            {{ 'detail.stock.by_warehouse' | translate }}
          </h4>
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-[length:var(--text-sm)]">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-3 py-2 text-left font-medium text-[var(--text-secondary)]">
                    {{ 'detail.stock.warehouse' | translate }}
                  </th>
                  <th class="px-3 py-2 text-right font-medium text-[var(--text-secondary)]">
                    {{ 'detail.stock.available_short' | translate }}
                  </th>
                  <th class="px-3 py-2 text-right font-medium text-[var(--text-secondary)]">
                    {{ 'detail.stock.reserved' | translate }}
                  </th>
                  <th class="px-3 py-2 text-right font-medium text-[var(--text-secondary)]">
                    {{ 'detail.stock.cover_short' | translate }}
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (wh of offer().warehouses; track wh.warehouseName) {
                  <tr class="border-b border-[var(--border-subtle)] last:border-b-0"
                      [class]="whRowClass(wh.daysOfCover)">
                    <td class="px-3 py-2 text-[var(--text-primary)]">{{ wh.warehouseName }}</td>
                    <td class="px-3 py-2 text-right font-mono text-[var(--text-primary)]">{{ wh.available }}</td>
                    <td class="px-3 py-2 text-right font-mono text-[var(--text-primary)]">{{ wh.reserved }}</td>
                    <td class="px-3 py-2 text-right font-mono text-[var(--text-primary)]">
                      @if (wh.daysOfCover !== null) {
                        {{ wh.daysOfCover.toFixed(1).replace('.', ',') }}
                      } @else {
                        —
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }

      <!-- Дополнительно -->
      <section>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'detail.stock.velocity' | translate }}
          </span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
            @if (offer().velocity14d !== null) {
              {{ offer().velocity14d!.toFixed(1).replace('.', ',') }} {{ 'detail.overview.units_per_day' | translate }}
            } @else {
              —
            }
          </span>

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'detail.stock.cost_price' | translate }}
          </span>
          <dp-money-display [value]="offer().costPrice" />
        </div>
      </section>
    </div>
  `,
})
export class OfferStockTabComponent {
  readonly offer = input.required<OfferDetail>();

  protected riskColor(risk: string): StatusColor {
    if (risk === 'CRITICAL') return 'error';
    if (risk === 'WARNING') return 'warning';
    return 'success';
  }

  protected whRowClass(daysOfCover: number | null): string {
    if (daysOfCover === null) return '';
    if (daysOfCover < 7) return 'bg-[color-mix(in_srgb,var(--status-error)_5%,transparent)]';
    if (daysOfCover < 14) return 'bg-[color-mix(in_srgb,var(--status-warning)_5%,transparent)]';
    return '';
  }
}
