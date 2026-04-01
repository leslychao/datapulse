import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { ActionApiService } from '@core/api/action-api.service';
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
      <div class="flex items-center justify-between">
        <h1 class="text-lg font-semibold text-[var(--text-primary)]">{{ 'execution.simulation.title' | translate }}</h1>
        <button
          (click)="showResetModal.set(true)"
          class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--status-error)] px-4 py-2 text-sm font-medium text-[var(--status-error)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]"
        >
          {{ 'execution.simulation.reset' | translate }}
        </button>
      </div>

      @if (simulationQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      } @else if (simulationQuery.isError()) {
        <dp-empty-state [message]="'execution.simulation.no_data' | translate" />
      } @else {
        <div class="grid grid-cols-4 gap-4">
          <dp-kpi-card [label]="'execution.simulation.kpi.current_margin' | translate" [value]="simulationQuery.data()?.currentMargin ?? '—'" />
          <dp-kpi-card [label]="'execution.simulation.kpi.shadow_margin' | translate" [value]="simulationQuery.data()?.shadowMargin ?? '—'" />
          <dp-kpi-card [label]="'execution.simulation.kpi.delta' | translate" [value]="simulationQuery.data()?.delta ?? '—'" />
          <dp-kpi-card [label]="'execution.simulation.kpi.affected' | translate" [value]="simulationQuery.data()?.affectedCount ?? 0" />
        </div>

        <dp-section-card [title]="'execution.simulation.margin_impact' | translate">
          <dp-chart [options]="marginChartOptions" height="300px" />
        </dp-section-card>
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
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  protected readonly showResetModal = signal(false);
  protected readonly marginChartOptions = {};

  protected readonly simulationQuery = injectQuery(() => ({
    queryKey: ['simulation', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.actionApi.getSimulationComparison()),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  protected readonly resetMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.actionApi.resetShadowState()),
    onSuccess: () => {
      this.showResetModal.set(false);
      this.simulationQuery.refetch();
      this.toast.success(this.translate.instant('execution.simulation.reset_success'));
    },
    onError: () => this.toast.error(this.translate.instant('common.error')),
  }));

  protected onResetConfirmed(): void {
    this.resetMutation.mutate(undefined);
  }
}
