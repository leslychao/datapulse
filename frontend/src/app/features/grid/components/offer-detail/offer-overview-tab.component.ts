import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { OfferDetail } from '@core/models';
import { CostProfileApiService } from '@core/api/cost-profile-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MoneyDisplayComponent } from '@shared/components/money-display.component';
import { PercentDisplayComponent } from '@shared/components/percent-display.component';
import { DateDisplayComponent } from '@shared/components/date-display.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';

@Component({
  selector: 'dp-offer-overview-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    StatusBadgeComponent,
    MoneyDisplayComponent,
    PercentDisplayComponent,
    DateDisplayComponent,
    MarketplaceBadgeComponent,
  ],
  template: `
    <div class="space-y-5 p-4">
      <!-- Основное -->
      <section>
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'detail.overview.general' | translate }}
        </h4>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.sku' | translate }}</span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().skuCode }}</span>

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.marketplace' | translate }}</span>
          <dp-marketplace-badge [type]="offer().marketplaceType" />

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.connection' | translate }}</span>
          <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().connectionName }}</span>

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.status' | translate }}</span>
          <dp-status-badge [label]="'grid.offer_status.' + offer().status | translate" [color]="offerStatusColor()" />

          @if (offer().category) {
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.category' | translate }}</span>
            <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().category }}</span>
          }
        </div>
      </section>

      <!-- Ценообразование -->
      <section>
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'detail.overview.pricing' | translate }}
        </h4>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.current_price' | translate }}</span>
          <dp-money-display [value]="offer().currentPrice" />

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.discount_price' | translate }}</span>
          <dp-money-display [value]="offer().discountPrice" />

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.cost_price' | translate }}</span>
          @if (editingCost()) {
            <div class="flex items-center gap-1">
              <input
                type="number"
                min="0.01"
                step="0.01"
                [ngModel]="costEditValue()"
                (ngModelChange)="costEditValue.set($event)"
                (blur)="saveCostEdit()"
                (keydown.enter)="saveCostEdit()"
                (keydown.escape)="editingCost.set(false)"
                class="w-24 rounded-[var(--radius-sm)] border border-[var(--accent-primary)] bg-[var(--bg-primary)] px-2 py-0.5 font-mono text-[length:var(--text-sm)] text-[var(--text-primary)] outline-none"
              />
              <span class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">₽</span>
            </div>
          } @else {
            <span
              class="cursor-pointer font-mono text-[length:var(--text-sm)] text-[var(--text-primary)] hover:text-[var(--accent-primary)]"
              [class.cursor-default]="!rbac.canEditCostProfiles()"
              (dblclick)="beginCostEdit()"
              [title]="rbac.canEditCostProfiles() ? ('detail.overview.cost_edit_hint' | translate) : ''"
            >
              @if (offer().costPrice !== null) {
                <dp-money-display [value]="offer().costPrice" />
              } @else {
                <span class="text-[var(--text-tertiary)]">—</span>
              }
            </span>
          }

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.margin' | translate }}</span>
          <dp-percent-display [value]="offer().marginPct" />

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.lock' | translate }}</span>
          <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">
            @if (offer().manualLock) {
              🔒
              @if (offer().lockedPrice !== null) {
                <dp-money-display [value]="offer().lockedPrice" />
              }
              @if (offer().lockReason) {
                <span class="text-[var(--text-secondary)]">· {{ offer().lockReason }}</span>
              }
            } @else {
              🔓 {{ 'detail.overview.no_lock' | translate }}
            }
          </span>
        </div>
      </section>

      <!-- Ценовая политика -->
      @if (offer().policyName) {
        <section>
          <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
            {{ 'detail.overview.policy' | translate }}
          </h4>
          <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.policy_name' | translate }}</span>
            <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().policyName }}</span>

            @if (offer().policyStrategy) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.strategy' | translate }}</span>
              <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().policyStrategy }}</span>
            }
            @if (offer().policyMode) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.mode' | translate }}</span>
              <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().policyMode }}</span>
            }
          </div>
        </section>
      }

      <!-- Последнее решение -->
      @if (offer().lastDecision) {
        <section>
          <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
            {{ 'detail.overview.last_decision' | translate }}
          </h4>
          <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.type' | translate }}</span>
            <dp-status-badge [label]="'grid.decision.' + offer().lastDecision | translate" [color]="decisionColor()" />

            @if (offer().lastDecisionDate) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.date' | translate }}</span>
              <dp-date-display [date]="offer().lastDecisionDate" [mode]="'absolute'" />
            }
          </div>
          @if (offer().lastDecisionExplanation) {
            <p class="mt-2 line-clamp-3 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ offer().lastDecisionExplanation }}
            </p>
          }
        </section>
      }

      <!-- Последнее действие -->
      @if (offer().lastActionStatus) {
        <section>
          <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
            {{ 'detail.overview.last_action' | translate }}
          </h4>
          <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.action_status' | translate }}</span>
            <dp-status-badge [label]="'grid.action_status.' + offer().lastActionStatus | translate" [color]="actionStatusColor()" />

            @if (offer().lastActionMode) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.action_mode' | translate }}</span>
              <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ offer().lastActionMode }}</span>
            }
          </div>
        </section>
      }

      <!-- Промо -->
      @if (offer().promoStatus; as promo) {
        <section>
          <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
            {{ 'detail.overview.promo' | translate }}
          </h4>
          <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.promo_status' | translate }}</span>
            <dp-status-badge
              [label]="(promo.participating ? 'grid.promo.PARTICIPATING' : 'grid.promo.ELIGIBLE') | translate"
              [color]="promo.participating ? 'success' : 'info'" />

            @if (promo.campaignName) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.promo_name' | translate }}</span>
              <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">{{ promo.campaignName }}</span>
            }
            @if (promo.promoPrice !== null) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.promo_price' | translate }}</span>
              <dp-money-display [value]="promo.promoPrice" />
            }
            @if (promo.endsAt) {
              <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.promo_end' | translate }}</span>
              <dp-date-display [date]="promo.endsAt" [mode]="'absolute'" />
            }
          </div>
        </section>
      }

      <!-- Аналитика -->
      <section>
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'detail.overview.analytics' | translate }}
        </h4>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.revenue' | translate }}</span>
          <dp-money-display [value]="offer().revenue30d" />

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.net_pnl' | translate }}</span>
          <dp-money-display [value]="offer().netPnl30d" [sign]="true" />

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.velocity' | translate }}</span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
            @if (offer().velocity14d !== null) {
              {{ offer().velocity14d!.toFixed(1).replace('.', ',') }} {{ 'detail.overview.units_per_day' | translate }}
            } @else {
              —
            }
          </span>

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.return_rate' | translate }}</span>
          <dp-percent-display [value]="offer().returnRatePct" />
        </div>
      </section>

      <!-- Остатки -->
      <section>
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'detail.overview.stock' | translate }}
        </h4>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.available' | translate }}</span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
            {{ offer().availableStock ?? '—' }}
          </span>

          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.days_cover' | translate }}</span>
          <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
            @if (offer().daysOfCover !== null) {
              {{ offer().daysOfCover!.toFixed(1).replace('.', ',') }}
            } @else {
              —
            }
          </span>

          @if (offer().stockRisk) {
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.stock_risk' | translate }}</span>
            <dp-status-badge [label]="'grid.stock_risk.' + offer().stockRisk | translate" [color]="stockRiskColor()" />
          }
        </div>
      </section>

      <!-- Данные -->
      <section>
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'detail.overview.data' | translate }}
        </h4>
        <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
          <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.sync' | translate }}</span>
          <dp-date-display [date]="offer().lastSyncAt" [mode]="'relative'" />

          @if (offer().dataFreshness) {
            <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">{{ 'detail.overview.freshness' | translate }}</span>
            <dp-status-badge
              [label]="offer().dataFreshness === 'FRESH' ? ('detail.overview.fresh' | translate) : ('detail.overview.stale' | translate)"
              [color]="offer().dataFreshness === 'FRESH' ? 'success' : 'error'"
            />
          }
        </div>
      </section>
    </div>
  `,
})
export class OfferOverviewTabComponent {
  readonly offer = input.required<OfferDetail>();

  protected readonly rbac = inject(RbacService);
  private readonly costApi = inject(CostProfileApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = injectQueryClient();

  readonly editingCost = signal(false);
  readonly costEditValue = signal<number | null>(null);

  private readonly costMutation = injectMutation(() => ({
    mutationFn: (params: { sellerSkuId: number; costPrice: number }) => {
      const today = new Date().toISOString().slice(0, 10);
      return lastValueFrom(this.costApi.bulkFormula({
        sellerSkuIds: [params.sellerSkuId],
        operation: 'FIXED',
        value: params.costPrice,
        validFrom: today,
      }));
    },
    onSuccess: () => {
      this.editingCost.set(false);
      this.toast.success(this.translate.instant('grid.cost.updated'));
      this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
    },
    onError: () => {
      this.editingCost.set(false);
      this.toast.error(this.translate.instant('grid.cost.update_error'));
    },
  }));

  beginCostEdit(): void {
    if (!this.rbac.canEditCostProfiles()) return;
    this.costEditValue.set(this.offer().costPrice ?? 0);
    this.editingCost.set(true);
  }

  saveCostEdit(): void {
    const val = this.costEditValue();
    if (val === null || val <= 0) {
      this.editingCost.set(false);
      return;
    }
    if (val === this.offer().costPrice) {
      this.editingCost.set(false);
      return;
    }
    this.costMutation.mutate({ sellerSkuId: this.offer().sellerSkuId, costPrice: val });
  }

  protected offerStatusColor(): StatusColor {
    switch (this.offer().status) {
      case 'ACTIVE': return 'success';
      case 'BLOCKED': return 'error';
      case 'INACTIVE': return 'neutral';
      default: return 'neutral';
    }
  }

  protected decisionColor(): StatusColor {
    switch (this.offer().lastDecision) {
      case 'CHANGE': return 'info';
      case 'HOLD': return 'warning';
      default: return 'neutral';
    }
  }

  protected actionStatusColor(): StatusColor {
    const s = this.offer().lastActionStatus;
    if (s === 'SUCCEEDED') return 'success';
    if (s === 'FAILED') return 'error';
    if (s === 'ON_HOLD' || s === 'RETRY_SCHEDULED') return 'warning';
    if (s === 'PENDING_APPROVAL' || s === 'EXECUTING' || s === 'RECONCILIATION_PENDING') return 'info';
    return 'neutral';
  }

  protected stockRiskColor(): StatusColor {
    switch (this.offer().stockRisk) {
      case 'CRITICAL': return 'error';
      case 'WARNING': return 'warning';
      default: return 'success';
    }
  }
}
