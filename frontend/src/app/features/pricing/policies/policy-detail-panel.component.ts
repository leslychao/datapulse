import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, X, ExternalLink } from 'lucide-angular';

import { PricingApiService } from '@core/api/pricing-api.service';
import {
  PricingPolicy,
  TargetMarginParams,
  PriceCorridorParams,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { formatDateTime, formatPercent, formatMoney } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-policy-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, RouterLink],
  template: `
    @if (policyQuery.isPending()) {
      <div class="flex h-full items-center justify-center p-6">
        <span
          class="dp-spinner inline-block h-8 w-8 rounded-full border-2 border-[var(--border-default)]"
          style="border-top-color: var(--accent-primary)"
        ></span>
      </div>
    } @else if (policyQuery.isError() || !policyQuery.data()) {
      <div class="p-4 text-[length:var(--text-sm)] text-[var(--status-error)]">
        {{ 'pricing.panel.load_error' | translate }}
      </div>
    } @else {
      @let p = policyQuery.data()!;
      <div class="flex h-full flex-col">
        <!-- Header -->
        <div class="shrink-0 border-b border-[var(--border-default)] px-4 py-3">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <p class="text-[length:var(--text-xs)] font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ ('pricing.policies.strategy.' + p.strategyType) | translate }}
              </p>
              <h3 class="mt-1 line-clamp-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
                {{ p.name }}
              </h3>
              <div class="mt-2 flex flex-wrap gap-2">
                <span
                  class="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium"
                  [style.color]="statusColor(p.status)"
                  [style.background-color]="statusBg(p.status)"
                >
                  <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="statusColor(p.status)"></span>
                  {{ ('pricing.policies.status.' + p.status) | translate }}
                </span>
                <span
                  class="inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium"
                  style="color: var(--text-primary); background-color: color-mix(in srgb, var(--text-tertiary) 12%, transparent)"
                >
                  v{{ p.version }}
                </span>
              </div>
            </div>
            <button
              type="button"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-[var(--radius-sm)] text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
              (click)="closePanel()"
              [attr.aria-label]="'detail_panel.close' | translate"
            >
              <lucide-icon [img]="closeIcon" [size]="16" />
            </button>
          </div>
        </div>

        <!-- Content -->
        <div class="flex-1 space-y-4 overflow-auto px-4 py-3">
          <!-- General info -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.panel.section.general' | translate }}
            </h4>
            <dl class="space-y-1.5 text-[length:var(--text-sm)]">
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.mode' | translate }}</dt>
                <dd class="text-right text-[var(--text-primary)]">
                  {{ ('pricing.policies.mode.' + p.executionMode) | translate }}
                </dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.priority' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.priority }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.assignments' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.assignmentsCount }}</dd>
              </div>
              @if (p.executionMode === 'SEMI_AUTO') {
                <div class="flex justify-between gap-4">
                  <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.approval_timeout' | translate }}</dt>
                  <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.approvalTimeoutHours }}h</dd>
                </div>
              }
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.created' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ fmtDate(p.createdAt) }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.updated' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ fmtDate(p.updatedAt) }}</dd>
              </div>
            </dl>
          </section>

          <!-- Strategy params -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.panel.section.strategy' | translate }}
            </h4>
            @if (p.strategyType === 'TARGET_MARGIN') {
              @let tm = targetMargin(p);
              <dl class="space-y-1.5 text-[length:var(--text-sm)]">
                <div class="flex justify-between gap-4">
                  <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.target_margin' | translate }}</dt>
                  <dd class="font-mono text-right text-[var(--text-primary)]">{{ fmtPct(tm.targetMarginPct) }}</dd>
                </div>
                <div class="flex justify-between gap-4">
                  <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.commission_source' | translate }}</dt>
                  <dd class="text-right text-[var(--text-primary)]">
                    {{ ('pricing.form.commission_source.' + tm.commissionSource) | translate }}
                  </dd>
                </div>
                <div class="flex justify-between gap-4">
                  <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.rounding' | translate }}</dt>
                  <dd class="font-mono text-right text-[var(--text-primary)]">
                    {{ tm.roundingStep }} · {{ ('pricing.form.rounding.' + tm.roundingDirection) | translate }}
                  </dd>
                </div>
              </dl>
            } @else {
              @let c = corridor(p);
              <dl class="space-y-1.5 text-[length:var(--text-sm)]">
                <div class="flex justify-between gap-4">
                  <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.corridor_min' | translate }}</dt>
                  <dd class="font-mono text-right text-[var(--text-primary)]">{{ c.corridorMinPrice != null ? fmtMoney(c.corridorMinPrice) : '—' }}</dd>
                </div>
                <div class="flex justify-between gap-4">
                  <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.corridor_max' | translate }}</dt>
                  <dd class="font-mono text-right text-[var(--text-primary)]">{{ c.corridorMaxPrice != null ? fmtMoney(c.corridorMaxPrice) : '—' }}</dd>
                </div>
              </dl>
            }
          </section>

          <!-- Constraints -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.panel.section.constraints' | translate }}
            </h4>
            <dl class="space-y-1.5 text-[length:var(--text-sm)]">
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.min_margin' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.minMarginPct != null ? fmtPct(p.minMarginPct) : '—' }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.max_change' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.maxPriceChangePct != null ? fmtPct(p.maxPriceChangePct) : '—' }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.min_price' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.minPrice != null ? fmtMoney(p.minPrice) : '—' }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.panel.max_price' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ p.maxPrice != null ? fmtMoney(p.maxPrice) : '—' }}</dd>
              </div>
            </dl>
          </section>

          <!-- Guards -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.panel.section.guards' | translate }}
            </h4>
            <div class="flex flex-wrap gap-1.5">
              @for (g of guardBadges(p); track g.key) {
                <span
                  class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
                  [class]="g.enabled
                    ? 'bg-[color-mix(in_srgb,var(--status-success)_12%,transparent)] text-[var(--status-success)]'
                    : 'bg-[var(--bg-tertiary)] text-[var(--text-tertiary)]'"
                >
                  <span class="inline-block h-1.5 w-1.5 rounded-full"
                    [style.background-color]="g.enabled ? 'var(--status-success)' : 'var(--text-tertiary)'"></span>
                  {{ g.label }}
                </span>
              }
            </div>
          </section>

          <!-- Links -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.panel.section.actions' | translate }}
            </h4>
            <div class="flex flex-col gap-2">
              <a
                [routerLink]="editRoute()"
                class="inline-flex items-center gap-2 text-[length:var(--text-sm)] text-[var(--accent-primary)] hover:underline"
              >
                <lucide-icon [img]="extLinkIcon" [size]="14" />
                {{ 'pricing.panel.open_edit' | translate }}
              </a>
              <a
                [routerLink]="assignmentsRoute()"
                class="inline-flex items-center gap-2 text-[length:var(--text-sm)] text-[var(--accent-primary)] hover:underline"
              >
                <lucide-icon [img]="extLinkIcon" [size]="14" />
                {{ 'pricing.panel.open_assignments' | translate }}
              </a>
            </div>
          </section>
        </div>
      </div>
    }
  `,
})
export class PolicyDetailPanelComponent {
  protected readonly panelService = inject(DetailPanelService);
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);

  readonly closeIcon = X;
  readonly extLinkIcon = ExternalLink;

  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId() ?? 0);

  readonly policyQuery = injectQuery(() => ({
    queryKey: ['policy', 'panel', this.panelService.entityId()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.getPolicy(
          this.workspaceId(),
          this.panelService.entityId()!,
        ),
      ),
    enabled:
      this.panelService.isOpen() &&
      this.panelService.entityType() === 'policy' &&
      this.panelService.entityId() != null,
  }));

  targetMargin(p: PricingPolicy): TargetMarginParams {
    return p.strategyParams as TargetMarginParams;
  }

  corridor(p: PricingPolicy): PriceCorridorParams {
    return p.strategyParams as PriceCorridorParams;
  }

  guardBadges(p: PricingPolicy): { key: string; label: string; enabled: boolean }[] {
    const gc = p.guardConfig;
    return [
      { key: 'margin', label: this.translate.instant('pricing.panel.guard.margin'), enabled: gc.marginGuardEnabled },
      { key: 'frequency', label: this.translate.instant('pricing.panel.guard.frequency'), enabled: gc.frequencyGuardEnabled },
      { key: 'volatility', label: this.translate.instant('pricing.panel.guard.volatility'), enabled: gc.volatilityGuardEnabled },
      { key: 'promo', label: this.translate.instant('pricing.panel.guard.promo'), enabled: gc.promoGuardEnabled },
      { key: 'stock_out', label: this.translate.instant('pricing.panel.guard.stock_out'), enabled: gc.stockOutGuardEnabled },
      { key: 'stale_data', label: this.translate.instant('pricing.panel.guard.stale_data'), enabled: gc.staleDataGuardHours > 0 },
    ];
  }

  editRoute(): string[] {
    return ['/workspace', String(this.workspaceId()), 'pricing', 'policies', String(this.panelService.entityId()), 'edit'];
  }

  assignmentsRoute(): string[] {
    return ['/workspace', String(this.workspaceId()), 'pricing', 'policies', String(this.panelService.entityId()), 'assignments'];
  }

  statusColor(status: string): string {
    const map: Record<string, string> = {
      ACTIVE: 'var(--status-success)',
      PAUSED: 'var(--status-warning)',
      DRAFT: 'var(--status-info)',
      ARCHIVED: 'var(--text-tertiary)',
    };
    return map[status] ?? 'var(--text-tertiary)';
  }

  statusBg(status: string): string {
    return `color-mix(in srgb, ${this.statusColor(status)} 12%, transparent)`;
  }

  fmtDate(iso: string): string {
    return formatDateTime(iso);
  }

  fmtPct(value: number): string {
    return formatPercent(value, 1, false);
  }

  fmtMoney(value: number): string {
    return formatMoney(value, 0);
  }

  closePanel(): void {
    this.panelService.close();
  }
}
