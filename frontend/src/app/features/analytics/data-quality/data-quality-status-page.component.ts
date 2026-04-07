import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { ConnectionDataQuality, SyncDomainStatus } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { formatDateTime, formatInteger } from '@shared/utils/format.utils';

const DOMAIN_LABEL_KEYS: Record<string, string> = {
  finance: 'analytics.data_quality.domain.finance',
  orders: 'analytics.data_quality.domain.orders',
  stock: 'analytics.data_quality.domain.stock',
  catalog: 'analytics.data_quality.domain.catalog',
  advertising: 'analytics.data_quality.domain.advertising',
};

const STATUS_DOT_CLASS: Record<SyncDomainStatus, string> = {
  FRESH: 'bg-[var(--status-success)]',
  STALE: 'bg-[var(--status-warning)]',
  OVERDUE: 'bg-[var(--status-error)]',
};

@Component({
  selector: 'dp-data-quality-status-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex h-full flex-col gap-4">
      @if (statusQuery.isPending()) {
        <div class="space-y-4 pb-4">
          @for (_ of [1, 2]; track $index) {
            <div class="dp-shimmer h-48 w-full rounded-[var(--radius-md)]"></div>
          }
        </div>
      } @else if (statusQuery.isError()) {
        <div class="text-sm text-[var(--status-error)]">
          {{ 'analytics.data_quality.load_error' | translate }}
        </div>
      } @else {
        <div class="space-y-6 pb-4">
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

                <span
                  class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
                  [class]="conn.automationBlocked
                    ? 'bg-[color-mix(in_srgb,var(--status-warning)_12%,transparent)] text-[var(--status-warning)]'
                    : 'bg-[color-mix(in_srgb,var(--status-success)_12%,transparent)] text-[var(--status-success)]'"
                >
                  {{ conn.automationBlocked
                    ? ('analytics.data_quality.automation_blocked' | translate)
                    : ('analytics.data_quality.automation_active' | translate) }}
                </span>
              </div>

              @if (conn.automationBlocked && conn.blockReason) {
                <div class="border-b border-[var(--border-default)] bg-[color-mix(in_srgb,var(--status-warning)_6%,transparent)] px-4 py-2 text-xs text-[var(--status-warning)]">
                  {{ conn.blockReason }}
                </div>
              }

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
                          {{ fmtDateTime(domain.lastSuccessAt) }}
                        </td>
                        <td class="px-4 py-2">
                          <span class="inline-flex items-center gap-1.5 text-[length:var(--text-xs)]">
                            <span
                              class="h-1.5 w-1.5 rounded-full"
                              [class]="syncStatusDotClass(domain.status)"
                            ></span>
                            {{ syncStatusLabel(domain.status) }}
                          </span>
                        </td>
                        <td class="px-4 py-2 text-right font-mono text-[var(--text-secondary)]">
                          {{ fmtInteger(domain.recordCount) }}
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
  private readonly t = inject(TranslateService);

  readonly statusQuery = injectQuery(() => ({
    queryKey: ['analytics', 'data-quality-status', this.wsStore.currentWorkspaceId()],
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
    const key = DOMAIN_LABEL_KEYS[domain];
    return key ? this.t.instant(key) : domain;
  }

  syncStatusDotClass(status: SyncDomainStatus): string {
    return STATUS_DOT_CLASS[status] ?? 'bg-[var(--text-tertiary)]';
  }

  syncStatusLabel(status: SyncDomainStatus): string {
    return this.t.instant(`analytics.data_quality.sync_status.${status}`);
  }

  fmtDateTime(iso: string | null): string {
    return formatDateTime(iso);
  }

  fmtInteger(value: number): string {
    return formatInteger(value);
  }
}
