import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import {
  injectMutation,
  injectQuery,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  Lightbulb,
  AlertTriangle,
  AlertCircle,
  TrendingUp,
  PackageOpen,
  BarChart3,
  Swords,
  Check,
  Filter,
} from 'lucide-angular';

import { PricingAiApiService } from '@core/api/pricing-ai-api.service';
import { InsightSeverity, InsightType, PricingInsight } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-insights-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, FormsModule],
  template: `
    <div class="flex h-full flex-col gap-4 overflow-auto p-4">
      <div class="flex items-center justify-between">
        <h1 class="text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
          {{ 'pricing.insights.title' | translate }}
        </h1>

        <div class="flex items-center gap-2">
          <select
            [(ngModel)]="filterType"
            (ngModelChange)="onFilterChange()"
            class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                   bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)]
                   text-[var(--text-primary)]"
          >
            <option [ngValue]="null">{{ 'pricing.insights.filter.all_types' | translate }}</option>
            @for (type of insightTypes; track type) {
              <option [ngValue]="type">
                {{ 'pricing.insights.types.' + type | translate }}
              </option>
            }
          </select>

          <select
            [(ngModel)]="filterAcknowledged"
            (ngModelChange)="onFilterChange()"
            class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                   bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)]
                   text-[var(--text-primary)]"
          >
            <option [ngValue]="undefined">{{ 'pricing.insights.filter.all' | translate }}</option>
            <option [ngValue]="false">{{ 'pricing.insights.filter.unread' | translate }}</option>
            <option [ngValue]="true">{{ 'pricing.insights.filter.read' | translate }}</option>
          </select>
        </div>
      </div>

      @if (insightsQuery.isPending()) {
        <div class="flex items-center justify-center py-12 text-[var(--text-secondary)]">
          <div class="h-6 w-6 animate-spin rounded-full border-2
                      border-[var(--accent-primary)] border-t-transparent"></div>
        </div>
      } @else if (insightsQuery.isError()) {
        <div class="rounded-[var(--radius-md)] bg-[var(--bg-secondary)] p-6 text-center
                    text-[var(--text-secondary)]">
          {{ 'pricing.insights.error' | translate }}
        </div>
      } @else if (insights().length === 0) {
        <div class="flex flex-col items-center gap-2 py-12 text-[var(--text-secondary)]">
          <lucide-icon [img]="LightbulbIcon" size="32" />
          <p class="text-[length:var(--text-sm)]">
            {{ 'pricing.insights.empty' | translate }}
          </p>
        </div>
      } @else {
        <div class="grid gap-3">
          @for (insight of insights(); track insight.id) {
            <div
              class="flex gap-3 rounded-[var(--radius-md)] border border-[var(--border-default)]
                     bg-[var(--bg-primary)] p-4 transition-colors"
              [class.opacity-60]="insight.acknowledged"
            >
              <div class="flex-shrink-0 pt-0.5">
                <lucide-icon
                  [img]="getTypeIcon(insight.insightType)"
                  size="18"
                  [class]="getSeverityColor(insight.severity)"
                />
              </div>

              <div class="min-w-0 flex-1">
                <div class="flex items-start justify-between gap-2">
                  <h3 class="text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
                    {{ insight.title }}
                  </h3>
                  <span class="flex-shrink-0 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                    {{ insight.createdAt | translate }}
                  </span>
                </div>

                <p class="mt-1 text-[length:var(--text-sm)] leading-relaxed
                          text-[var(--text-secondary)]">
                  {{ insight.body }}
                </p>

                <div class="mt-2 flex items-center gap-3">
                  <span class="inline-flex items-center rounded-full px-2 py-0.5
                               text-[length:var(--text-xs)] font-medium
                               bg-[var(--bg-tertiary)] text-[var(--text-secondary)]">
                    {{ 'pricing.insights.types.' + insight.insightType | translate }}
                  </span>

                  @if (!insight.acknowledged) {
                    <button
                      type="button"
                      class="inline-flex items-center gap-1 text-[length:var(--text-xs)]
                             font-medium text-[var(--accent-primary)]
                             hover:text-[var(--accent-primary-hover)] transition-colors"
                      (click)="acknowledge(insight.id)"
                      [disabled]="acknowledgeMutation.isPending()"
                    >
                      <lucide-icon [img]="CheckIcon" size="12" />
                      {{ 'pricing.insights.acknowledge' | translate }}
                    </button>
                  }
                </div>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class InsightsPageComponent {
  private readonly aiApi = inject(PricingAiApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);

  filterType = signal<InsightType | null>(null);
  filterAcknowledged = signal<boolean | undefined>(undefined);

  readonly insightTypes: InsightType[] = [
    'PRICE_INCREASE_CANDIDATE',
    'OVERSTOCK_LIQUIDATION',
    'HIGH_DRR_ALERT',
    'COMPETITOR_UNDERCUT',
  ];

  readonly LightbulbIcon = Lightbulb;
  readonly CheckIcon = Check;
  readonly FilterIcon = Filter;

  readonly insightsQuery = injectQuery(() => ({
    queryKey: [
      'pricing-insights',
      this.wsStore.currentWorkspaceId(),
      this.filterType(),
      this.filterAcknowledged(),
    ],
    queryFn: () => {
      const wsId = this.wsStore.currentWorkspaceId();
      if (!wsId) return Promise.resolve({ content: [], totalElements: 0 });
      return lastValueFrom(
        this.aiApi.listInsights(
          wsId,
          this.filterType() ?? undefined,
          this.filterAcknowledged(),
        ),
      );
    },
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly insights = computed(() => {
    const data = this.insightsQuery.data();
    return data?.content ?? [];
  });

  readonly acknowledgeMutation = injectMutation(() => ({
    mutationFn: (insightId: number) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      return lastValueFrom(this.aiApi.acknowledgeInsight(wsId, insightId));
    },
    onSuccess: () => {
      this.insightsQuery.refetch();
    },
    onError: () => {
      this.toast.error('pricing.insights.acknowledge_error');
    },
  }));

  onFilterChange(): void {
    this.insightsQuery.refetch();
  }

  acknowledge(insightId: number): void {
    this.acknowledgeMutation.mutate(insightId);
  }

  getTypeIcon(type: InsightType) {
    switch (type) {
      case 'PRICE_INCREASE_CANDIDATE':
        return TrendingUp;
      case 'OVERSTOCK_LIQUIDATION':
        return PackageOpen;
      case 'HIGH_DRR_ALERT':
        return BarChart3;
      case 'COMPETITOR_UNDERCUT':
        return Swords;
    }
  }

  getSeverityColor(severity: InsightSeverity): string {
    switch (severity) {
      case 'CRITICAL':
        return 'text-[var(--status-error)]';
      case 'WARNING':
        return 'text-[var(--status-warning)]';
      case 'INFO':
        return 'text-[var(--status-info)]';
    }
  }
}
