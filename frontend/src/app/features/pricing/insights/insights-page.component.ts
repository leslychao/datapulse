import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import {
  injectMutation,
  injectQuery,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  Lightbulb,
  TrendingUp,
  PackageOpen,
  BarChart3,
  Swords,
  Check,
} from 'lucide-angular';

import { PricingAiApiService } from '@core/api/pricing-ai-api.service';
import { InsightSeverity, InsightType, Page, PricingInsight } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import {
  FilterBarUrlDef,
  readFilterBarFromUrl,
  syncFilterBarToUrl,
} from '@shared/utils/url-filters';

@Component({
  selector: 'dp-insights-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DatePipe, LucideAngularModule, FilterBarComponent, EmptyStateComponent],
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h1 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'pricing.insights.title' | translate }}
        </h1>
      </div>

      <div class="border-b border-[var(--border-default)] px-4 py-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 overflow-auto p-4">
        @if (insightsQuery.isPending()) {
          <div class="flex items-center justify-center py-12 text-[var(--text-secondary)]">
            <div class="h-6 w-6 animate-spin rounded-full border-2
                        border-[var(--accent-primary)] border-t-transparent"></div>
          </div>
        } @else if (insightsQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.insights.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="insightsQuery.refetch()"
          />
        } @else if (insights().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('pricing.insights.empty_filtered' | translate)
              : ('pricing.insights.empty' | translate)"
            [actionLabel]="hasActiveFilters() ? ('filter_bar.reset_all' | translate) : ''"
            (action)="onFiltersChanged({})"
          />
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
                      {{ insight.createdAt | date:'dd.MM.yyyy HH:mm' }}
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
    </div>
  `,
})
export class InsightsPageComponent {
  private readonly aiApi = inject(PricingAiApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly filterValues = signal<Record<string, any>>({});

  private readonly filterBarUrlDefs: FilterBarUrlDef[] = [
    { key: 'insightType', type: 'string' },
    { key: 'acknowledged', type: 'string' },
  ];

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'insightType',
      label: 'pricing.insights.filter.all_types',
      type: 'select',
      options: ([
        'PRICE_INCREASE_CANDIDATE',
        'OVERSTOCK_LIQUIDATION',
        'HIGH_DRR_ALERT',
        'COMPETITOR_UNDERCUT',
      ] as const).map((value) => ({
        value,
        label: `pricing.insights.types.${value}`,
      })),
    },
    {
      key: 'acknowledged',
      label: 'pricing.insights.filter.all',
      type: 'select',
      options: [
        { value: 'false', label: 'pricing.insights.filter.unread' },
        { value: 'true', label: 'pricing.insights.filter.read' },
      ],
    },
  ];

  constructor() {
    readFilterBarFromUrl(this.route, this.filterValues, this.filterBarUrlDefs);
    syncFilterBarToUrl(this.router, this.route, this.filterValues, this.filterBarUrlDefs);
  }

  readonly LightbulbIcon = Lightbulb;
  readonly CheckIcon = Check;

  private readonly activeFilterType = computed<InsightType | undefined>(() => {
    const v = this.filterValues()['insightType'];
    return v || undefined;
  });

  private readonly activeAcknowledged = computed<boolean | undefined>(() => {
    const v = this.filterValues()['acknowledged'];
    if (v === 'true') return true;
    if (v === 'false') return false;
    return undefined;
  });

  readonly insightsQuery = injectQuery(() => ({
    queryKey: [
      'pricing-insights',
      this.wsStore.currentWorkspaceId(),
      this.activeFilterType(),
      this.activeAcknowledged(),
    ],
    queryFn: (): Promise<Page<PricingInsight>> => {
      const wsId = this.wsStore.currentWorkspaceId();
      if (!wsId) {
        return Promise.resolve({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 0,
        });
      }
      return lastValueFrom(
        this.aiApi.listInsights(
          wsId,
          this.activeFilterType(),
          this.activeAcknowledged(),
        ),
      );
    },
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly insights = computed(() => this.insightsQuery.data()?.content ?? []);

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined,
    ),
  );

  readonly acknowledgeMutation = injectMutation(() => ({
    mutationFn: (insightId: number) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      return lastValueFrom(this.aiApi.acknowledgeInsight(wsId, insightId));
    },
    onSuccess: () => {
      this.insightsQuery.refetch();
    },
    onError: () => {
      this.toast.error(this.translate.instant('pricing.insights.acknowledge_error'));
    },
  }));

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
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
