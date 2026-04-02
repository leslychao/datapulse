import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { BarChart3, Percent, TrendingUp, Target } from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { SimulationComparison } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-simulation-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe, KpiCardComponent, SectionCardComponent, ChartComponent,
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
              [value]="connectionId()"
              (change)="onConnectionChange($event)"
            >
              <option [value]="0" disabled>{{ 'execution.simulation.select_connection' | translate }}</option>
              @for (conn of connections; track conn.id) {
                <option [value]="conn.id">{{ conn.name }}</option>
              }
            </select>
          }
        </div>
      </div>

      @if (simulationQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      } @else if (simulationQuery.isError()) {
        <dp-empty-state [message]="'execution.simulation.empty_no_policies' | translate" />
      } @else if (!simulationQuery.data() || simulationQuery.data()!.simulatedActionsCount === 0) {
        <dp-empty-state [message]="'execution.simulation.empty_no_results' | translate" />
      } @else {
        @if (simulationQuery.data(); as sim) {
          <!-- KPI Strip -->
          <div class="grid grid-cols-4 gap-4">
            <dp-kpi-card
              [label]="'execution.simulation.kpi.sim_actions' | translate"
              [value]="sim.simulatedActionsCount.toLocaleString('ru-RU')"
              [icon]="BarChart3Icon"
              accent="primary"
            />
            <dp-kpi-card
              [label]="'execution.simulation.kpi.avg_delta' | translate"
              [value]="formatAvgDelta(sim.averagePriceDeltaPct)"
              [icon]="PercentIcon"
              [accent]="sim.averagePriceDeltaPct >= 0 ? 'success' : 'error'"
            />
            <dp-kpi-card
              [label]="'execution.simulation.kpi.direction' | translate"
              [value]="formatDirection(sim.directionDistribution)"
              [icon]="TrendingUpIcon"
              accent="info"
            />
            <dp-kpi-card
              [label]="'execution.simulation.kpi.coverage' | translate"
              [value]="formatCoverage(sim.coveragePct)"
              [subtitle]="translate.instant('execution.simulation.kpi.coverage_detail', { covered: sim.coveredOffers, total: sim.totalOffers })"
              [icon]="TargetIcon"
              [accent]="sim.coveragePct < 0.5 ? 'warning' : 'primary'"
            />
          </div>

          <!-- Margin Impact Chart -->
          <dp-section-card [title]="'execution.simulation.margin_impact' | translate">
            <dp-chart [options]="marginChartOptions()" height="300px" />
          </dp-section-card>

          <!-- Distribution Chart -->
          <dp-section-card [title]="'execution.simulation.distribution_title' | translate">
            <dp-chart [options]="distributionChartOptions()" height="250px" />
          </dp-section-card>
        }
      }

      <!-- Danger Zone -->
      @if (rbac.canResetSimulation()) {
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
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  protected readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  protected readonly BarChart3Icon = BarChart3;
  protected readonly PercentIcon = Percent;
  protected readonly TrendingUpIcon = TrendingUp;
  protected readonly TargetIcon = Target;

  protected readonly connectionId = signal(0);
  protected readonly showResetModal = signal(false);

  protected readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    staleTime: 120_000,
  }));

  private readonly canQuery = computed(
    () => !!this.wsStore.currentWorkspaceId() && this.connectionId() > 0,
  );

  protected readonly simulationQuery = injectQuery(() => {
    const wsId = this.wsStore.currentWorkspaceId();
    const connId = this.connectionId();
    return {
      queryKey: ['simulation-comparison', wsId, connId],
      queryFn: () => lastValueFrom(this.actionApi.getSimulationComparison(wsId!, connId)),
      enabled: this.canQuery(),
      staleTime: 300_000,
      refetchOnWindowFocus: true,
    };
  });

  protected readonly marginChartOptions = computed(() => {
    const sim = this.simulationQuery.data();
    if (!sim) return {};
    const breakdown = sim.perConnectionBreakdown;
    return {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: breakdown.map(b => b.connectionName),
      },
      series: [{
        type: 'bar',
        data: breakdown.map(b => ({
          value: b.marginImpact,
          itemStyle: {
            color: b.marginImpact >= 0
              ? 'var(--finance-positive)'
              : 'var(--finance-negative)',
          },
        })),
        label: {
          show: true,
          position: 'right',
          formatter: (p: any) => {
            const v = p.value;
            return `${v >= 0 ? '+' : ''}${v.toLocaleString('ru-RU')}₽`;
          },
        },
      }],
      grid: { left: 120, right: 100, top: 10, bottom: 10 },
    };
  });

  protected readonly distributionChartOptions = computed(() => {
    const sim = this.simulationQuery.data();
    if (!sim) return {};
    const dist = sim.deltaDistribution;
    return {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: dist.map(d => d.bucket),
      },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: dist.map(d => d.count),
        itemStyle: { color: 'var(--accent-primary)', borderRadius: [4, 4, 0, 0] },
      }],
      grid: { left: 50, right: 20, top: 10, bottom: 30 },
    };
  });

  protected readonly resetMutation = injectMutation(() => ({
    mutationFn: () => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      const connId = this.connectionId();
      return lastValueFrom(this.actionApi.resetShadowState(wsId, connId));
    },
    onSuccess: () => {
      this.showResetModal.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['simulation-comparison'] });
      this.toast.success(this.translate.instant('execution.simulation.reset_success'));
    },
    onError: () => this.toast.error(this.translate.instant('common.error')),
  }));

  protected onConnectionChange(event: Event): void {
    this.connectionId.set(Number((event.target as HTMLSelectElement).value));
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

  protected formatDirection(dist: SimulationComparison['directionDistribution']): string {
    const fmt = (v: number) => `${Math.round(v * 100)}%`;
    return `↑ ${fmt(dist.up)}  ↓ ${fmt(dist.down)}  → ${fmt(dist.unchanged)}`;
  }

  protected formatCoverage(pct: number): string {
    return `${Math.round(pct * 100)}%`;
  }
}
