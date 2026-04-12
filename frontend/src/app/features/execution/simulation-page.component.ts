import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { Router } from '@angular/router';
import { BarChart3, Percent, TrendingUp, Target } from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-simulation-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe, DecimalPipe, DatePipe,
    KpiCardComponent, SectionCardComponent,
    ConfirmationModalComponent, EmptyStateComponent, SpinnerComponent,
  ],
  template: `
    <div class="flex flex-col gap-6 p-6">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <h1 class="text-lg font-semibold text-[var(--text-primary)]">{{ 'execution.simulation.title' | translate }}</h1>
          @if (connectionsQuery.data(); as connections) {
            <select
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              [value]="selectedPlatform()"
              (change)="onConnectionChange($event)"
            >
              <option [value]="''" disabled>{{ 'execution.simulation.select_connection' | translate }}</option>
              @for (conn of connections; track conn.id) {
                <option [value]="conn.marketplaceType">{{ conn.name }}</option>
              }
            </select>
          }
        </div>
      </div>

      @if (connectionsQuery.isPending() || (canQuery() && simulationQuery.isPending())) {
        <dp-spinner [message]="'common.loading' | translate" />
      } @else if (!canQuery()) {
        <dp-empty-state [message]="'execution.simulation.select_connection' | translate" />
      } @else if (simulationQuery.isError()) {
        <dp-empty-state
          [message]="'execution.simulation.load_error' | translate"
          [actionLabel]="'execution.simulation.retry' | translate"
          (action)="simulationQuery.refetch()"
        />
      } @else if (!hasResults()) {
        <dp-empty-state [message]="'execution.simulation.empty_no_results' | translate" />
      } @else {
        @if (simulationQuery.data(); as report) {
          <!-- KPI Strip -->
          <div class="grid grid-cols-4 gap-4">
            <dp-kpi-card
              [label]="'execution.simulation.kpi.sim_actions' | translate"
              [value]="report.summary.totalSimulatedActions.toLocaleString('ru-RU')"
              [icon]="BarChart3Icon"
              accent="primary"
            />
            <dp-kpi-card
              [label]="'execution.simulation.kpi.avg_delta' | translate"
              [value]="formatAvgDelta(report.summary.avgDeltaPct)"
              [icon]="PercentIcon"
              [accent]="report.summary.avgDeltaPct >= 0 ? 'success' : 'error'"
            />
            <dp-kpi-card
              [label]="'execution.simulation.kpi.direction' | translate"
              [value]="formatDirection(report.summary)"
              [icon]="TrendingUpIcon"
              accent="info"
            />
            <dp-kpi-card
              [label]="'execution.simulation.kpi.coverage' | translate"
              [value]="formatCoverage(report.summary.coveragePct)"
              [subtitle]="translate.instant('execution.simulation.kpi.coverage_detail', { covered: report.summary.simulatedOfferCount, total: report.summary.totalOfferCount })"
              [icon]="TargetIcon"
              [accent]="report.summary.coveragePct < 0.5 ? 'warning' : 'primary'"
              [tooltip]="report.summary.coveragePct < 0.5 ? ('execution.simulation.kpi.coverage_low_tooltip' | translate) : ''"
            />
          </div>

          <!-- Items Table -->
          @if (report.items.length) {
            <dp-section-card [title]="'execution.simulation.items_title' | translate">
              <div class="dp-table-wrap overflow-x-auto">
                <table class="dp-table dp-table-compact">
                  <thead>
                    <tr>
                      <th>{{ 'execution.simulation.col.sku' | translate }}</th>
                      <th class="text-right">{{ 'execution.simulation.col.current_price' | translate }}</th>
                      <th class="text-right">{{ 'execution.simulation.col.price_at_simulation' | translate }}</th>
                      <th class="text-right">{{ 'execution.simulation.col.simulated_price' | translate }}</th>
                      <th class="text-right">{{ 'execution.simulation.col.delta' | translate }}</th>
                      <th class="text-right">{{ 'execution.simulation.col.delta_pct' | translate }}</th>
                      <th class="text-right">{{ 'execution.simulation.col.simulated_at' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (item of report.items; track item.marketplaceOfferId) {
                      <tr>
                        <td class="font-mono text-[var(--text-primary)]">{{ item.marketplaceSku }}</td>
                        <td class="text-right font-mono text-[var(--text-secondary)]">
                          @if (item.currentRealPrice != null) {
                            {{ item.currentRealPrice | number:'1.0-2':'ru' }} ₽
                          } @else {
                            <span class="text-[var(--text-tertiary)]">—</span>
                          }
                        </td>
                        <td class="text-right font-mono text-[var(--text-tertiary)]">{{ item.canonicalPriceAtSimulation | number:'1.0-2':'ru' }} ₽</td>
                        <td class="text-right font-mono text-[var(--text-primary)]">{{ item.simulatedPrice | number:'1.0-2':'ru' }} ₽</td>
                        <td class="text-right font-mono"
                            [class]="item.priceDelta >= 0 ? 'text-[var(--finance-positive)]' : 'text-[var(--finance-negative)]'">
                          {{ item.priceDelta >= 0 ? '+' : '' }}{{ item.priceDelta | number:'1.0-2':'ru' }} ₽
                        </td>
                        <td class="text-right font-mono"
                            [class]="item.priceDeltaPct >= 0 ? 'text-[var(--finance-positive)]' : 'text-[var(--finance-negative)]'">
                          {{ item.priceDeltaPct >= 0 ? '+' : '' }}{{ item.priceDeltaPct | number:'1.1-1':'ru' }}%
                        </td>
                        <td class="text-right text-[var(--text-secondary)]">{{ item.simulatedAt | date:'dd.MM.yy HH:mm' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </dp-section-card>
          }
        }
      }

      <!-- Danger Zone -->
      @if (canQuery() && rbac.canResetSimulation()) {
        <div class="rounded-[var(--radius-lg)] border-2 border-dashed border-[var(--status-error)] p-5">
          <h3 class="mb-2 text-sm font-semibold text-[var(--status-error)]">{{ 'execution.simulation.danger_zone_title' | translate }}</h3>
          <p class="mb-4 text-sm text-[var(--text-secondary)]">{{ 'execution.simulation.danger_zone_desc' | translate }}</p>
          <button
            (click)="showResetModal.set(true)"
            class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--status-error)] px-4 py-2 text-sm font-medium text-[var(--status-error)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]"
          >
            {{ 'execution.simulation.reset' | translate }}
          </button>
        </div>
      }

      <dp-confirmation-modal
        [open]="showResetModal()"
        [title]="'execution.simulation.reset_title' | translate"
        [message]="'execution.simulation.reset_message' | translate"
        [danger]="true"
        [typeToConfirm]="'execution.simulation.reset_confirm_word' | translate"
        (confirmed)="onResetConfirmed()"
        (cancelled)="showResetModal.set(false)"
      />
    </div>
  `,
})
export class SimulationPageComponent {
  private readonly actionApi = inject(ActionApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  protected readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  protected readonly BarChart3Icon = BarChart3;
  protected readonly PercentIcon = Percent;
  protected readonly TrendingUpIcon = TrendingUp;
  protected readonly TargetIcon = Target;

  protected readonly selectedPlatform = signal('');
  protected readonly showResetModal = signal(false);

  protected readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    staleTime: 120_000,
  }));

  protected readonly canQuery = computed(
    () => !!this.wsStore.currentWorkspaceId() && this.selectedPlatform() !== '',
  );

  constructor() {
    effect(() => {
      const connections = this.connectionsQuery.data();
      if (connections?.length && this.selectedPlatform() === '') {
        this.selectedPlatform.set(connections[0].marketplaceType);
      }
    });
  }

  protected readonly simulationQuery = injectQuery(() => {
    const wsId = this.wsStore.currentWorkspaceId();
    const platform = this.selectedPlatform();
    return {
      queryKey: ['simulation-comparison', wsId, platform],
      queryFn: () => lastValueFrom(this.actionApi.getSimulationComparison(wsId!, platform)),
      enabled: this.canQuery(),
      staleTime: 300_000,
      refetchOnWindowFocus: true,
    };
  });

  protected readonly hasResults = computed(() => {
    const data = this.simulationQuery.data();
    return !!data && data.summary.totalSimulatedActions > 0;
  });

  protected readonly resetMutation = injectMutation(() => ({
    mutationFn: () => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      const platform = this.selectedPlatform();
      return lastValueFrom(this.actionApi.resetShadowState(wsId, platform));
    },
    onSuccess: () => {
      this.showResetModal.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['simulation-comparison'] });
      this.toast.success(this.translate.instant('execution.simulation.reset_success'));
    },
    onError: () => this.toast.error(this.translate.instant('common.error')),
  }));

  protected navigateToPricing(): void {
    this.router.navigate(['/workspace', this.wsStore.currentWorkspaceId(), 'pricing', 'policies']);
  }

  protected onConnectionChange(event: Event): void {
    this.selectedPlatform.set((event.target as HTMLSelectElement).value);
  }

  protected onResetConfirmed(): void {
    this.resetMutation.mutate(undefined);
  }

  protected formatAvgDelta(pct: number): string {
    const abs = Math.abs(pct).toFixed(1).replace('.', ',');
    if (pct > 0) return `↑ ${abs}%`;
    if (pct < 0) return `↓ ${abs}%`;
    return `→ 0%`;
  }

  protected formatDirection(summary: { countIncrease: number; countDecrease: number; countUnchanged: number }): string {
    return `↑ ${summary.countIncrease}  ↓ ${summary.countDecrease}  → ${summary.countUnchanged}`;
  }

  protected formatCoverage(pct: number): string {
    return `${Math.round(pct * 100)}%`;
  }
}
