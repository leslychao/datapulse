import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { ConnectionDataQuality, SyncDomainStatus } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

const DOMAIN_LABELS: Record<string, string> = {
  finance: 'Финансы',
  orders: 'Заказы',
  stock: 'Остатки',
  catalog: 'Каталог',
  advertising: 'Реклама',
};

@Component({
  selector: 'dp-data-quality-status-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex h-full flex-col gap-4">
      @if (statusQuery.isPending()) {
        <div class="space-y-4 px-4 pb-4">
          @for (_ of [1, 2]; track $index) {
            <div class="dp-shimmer h-48 w-full rounded-[var(--radius-md)]"></div>
          }
        </div>
      } @else if (statusQuery.isError()) {
        <div class="px-4 text-sm text-[var(--status-error)]">
          {{ 'analytics.data_quality.load_error' | translate }}
        </div>
      } @else {
        <div class="space-y-6 px-4 pb-4">
          @for (conn of connections(); track conn.connectionId) {
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]">
              <!-- Connection Header -->
              <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-3">
                <div class="flex items-center gap-3">
                  <span class="text-sm font-medium text-[var(--text-primary)]">
                    {{ conn.connectionName }}
                  </span>
                  <span class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                    [style.background]="conn.marketplaceType === 'WB'
                      ? 'color-mix(in srgb, var(--status-info) 12%, transparent)'
                      : 'color-mix(in srgb, var(--status-success) 12%, transparent)'"
                    [style.color]="conn.marketplaceType === 'WB'
                      ? 'var(--status-info)' : 'var(--status-success)'"
                  >
                    {{ conn.marketplaceType }}
                  </span>
                </div>

                <!-- Automation Status -->
                <span
                  class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
                  [class]="conn.automationBlocked
                    ? 'bg-[color-mix(in_srgb,var(--status-warning)_12%,transparent)] text-[var(--status-warning)]'
                    : 'bg-[color-mix(in_srgb,var(--status-success)_12%,transparent)] text-[var(--status-success)]'"
                >
                  {{ conn.automationBlocked ? '⚠ BLOCKED' : '✅ Active' }}
                </span>
              </div>

              @if (conn.automationBlocked && conn.blockReason) {
                <div class="border-b border-[var(--border-default)] bg-[color-mix(in_srgb,var(--status-warning)_6%,transparent)] px-4 py-2 text-xs text-[var(--status-warning)]">
                  {{ conn.blockReason }}
                </div>
              }

              <!-- Sync Domain Table -->
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-[var(--border-subtle)] text-left text-[11px] uppercase tracking-wide text-[var(--text-tertiary)]">
                      <th class="px-4 py-2 font-medium">{{ 'analytics.data_quality.domain' | translate }}</th>
                      <th class="px-4 py-2 font-medium">{{ 'analytics.data_quality.last_sync' | translate }}</th>
                      <th class="px-4 py-2 font-medium">{{ 'analytics.data_quality.status' | translate }}</th>
                      <th class="px-4 py-2 text-right font-medium">{{ 'analytics.data_quality.records' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (domain of conn.domains; track domain.domain) {
                      <tr class="border-b border-[var(--border-subtle)] last:border-b-0">
                        <td class="px-4 py-2 text-[var(--text-primary)]">
                          {{ domainLabel(domain.domain) }}
                        </td>
                        <td class="px-4 py-2 text-[var(--text-secondary)]">
                          {{ domain.lastSuccessAt ?? '—' }}
                        </td>
                        <td class="px-4 py-2">
                          <span class="inline-flex items-center gap-1.5">
                            <span
                              class="h-2 w-2 rounded-full"
                              [class]="syncStatusDotClass(domain.status)"
                            ></span>
                            {{ syncStatusLabel(domain.status) }}
                          </span>
                        </td>
                        <td class="px-4 py-2 text-right font-mono text-[var(--text-secondary)]">
                          {{ domain.recordCount.toLocaleString('ru-RU') }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }

          @if (connections().length === 0) {
            <div class="py-12 text-center text-sm text-[var(--text-tertiary)]">
              {{ 'analytics.data_quality.no_connections' | translate }}
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class DataQualityStatusPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly statusQuery = injectQuery(() => ({
    queryKey: ['data-quality-status', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getDataQualityStatus(
          this.wsStore.currentWorkspaceId()!,
          {},
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly connections = computed<ConnectionDataQuality[]>(() =>
    this.statusQuery.data()?.connections ?? [],
  );

  domainLabel(domain: string): string {
    return DOMAIN_LABELS[domain] ?? domain;
  }

  syncStatusDotClass(status: SyncDomainStatus): string {
    switch (status) {
      case 'FRESH': return 'bg-[var(--status-success)]';
      case 'STALE': return 'bg-[var(--status-warning)]';
      case 'OVERDUE': return 'bg-[var(--status-error)]';
    }
  }

  syncStatusLabel(status: SyncDomainStatus): string {
    switch (status) {
      case 'FRESH': return 'Актуально';
      case 'STALE': return 'Устарело';
      case 'OVERDUE': return 'Просрочено';
    }
  }
}
