import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

@Component({
  selector: 'dp-bidding-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DecimalPipe],
  template: `
    <div class="flex flex-col gap-6 overflow-y-auto p-6">
      @if (dashboardQuery.isPending()) {
        <div class="flex items-center justify-center py-12">
          <div class="h-6 w-6 animate-spin rounded-full border-2 border-[var(--accent-primary)] border-t-transparent"></div>
        </div>
      }

      @if (dashboardQuery.isError()) {
        <div class="rounded-[var(--radius-md)] bg-[var(--bg-secondary)] p-6 text-center text-[var(--text-secondary)]">
          {{ 'bidding.dashboard.error' | translate }}
        </div>
      }

      @if (dashboardQuery.data(); as d) {
        <!-- KPI cards -->
        <div class="grid grid-cols-4 gap-4">
          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <div class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'bidding.dashboard.managed_products' | translate }}
            </div>
            <div class="mt-1 text-2xl font-semibold text-[var(--text-primary)]">
              {{ d.totalManagedProducts | number }}
            </div>
          </div>
          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <div class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'bidding.dashboard.active_policies' | translate }}
            </div>
            <div class="mt-1 text-2xl font-semibold text-[var(--text-primary)]">
              {{ d.activePolicies | number }}
            </div>
          </div>
          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <div class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'bidding.dashboard.runs_7d' | translate }}
            </div>
            <div class="mt-1 text-2xl font-semibold text-[var(--text-primary)]">
              {{ d.totalRunsLast7d | number }}
            </div>
          </div>
          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <div class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'bidding.dashboard.failed_runs' | translate }}
            </div>
            <div class="mt-1 text-2xl font-semibold"
                 [class]="d.failedRunsLast7d > 0 ? 'text-[var(--status-error)]' : 'text-[var(--text-primary)]'">
              {{ d.failedRunsLast7d | number }}
            </div>
          </div>
        </div>

        <!-- Decisions breakdown + Products by strategy -->
        <div class="grid grid-cols-2 gap-4">
          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <h3 class="mb-3 text-[length:var(--text-base)] font-medium text-[var(--text-primary)]">
              {{ 'bidding.dashboard.decisions_7d' | translate }}
            </h3>
            <div class="flex flex-col gap-2">
              @for (entry of decisionEntries(d.decisionsByType); track entry.key) {
                <div class="flex items-center justify-between">
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'bidding.decision_type.' + entry.key | translate }}
                  </span>
                  <span class="font-mono text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
                    {{ entry.value | number }}
                  </span>
                </div>
              }
              @if (decisionEntries(d.decisionsByType).length === 0) {
                <div class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                  {{ 'bidding.dashboard.no_decisions' | translate }}
                </div>
              }
            </div>
          </div>

          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <h3 class="mb-3 text-[length:var(--text-base)] font-medium text-[var(--text-primary)]">
              {{ 'bidding.dashboard.products_by_strategy' | translate }}
            </h3>
            <div class="flex flex-col gap-2">
              @for (entry of decisionEntries(d.productsByStrategy); track entry.key) {
                <div class="flex items-center justify-between">
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'bidding.policies.strategy.' + entry.key | translate }}
                  </span>
                  <span class="font-mono text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
                    {{ entry.value | number }}
                  </span>
                </div>
              }
            </div>
          </div>
        </div>

        <!-- Top tables -->
        <div class="grid grid-cols-2 gap-4">
          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <h3 class="mb-3 text-[length:var(--text-base)] font-medium text-[var(--text-primary)]">
              {{ 'bidding.dashboard.top_high_drr' | translate }}
            </h3>
            @if (d.topHighDrr.length === 0) {
              <div class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'bidding.dashboard.no_data' | translate }}
              </div>
            } @else {
              <table class="w-full text-[length:var(--text-sm)]">
                <thead>
                  <tr class="border-b border-[var(--border-subtle)] text-left text-[var(--text-secondary)]">
                    <th class="pb-2 font-medium">SKU</th>
                    <th class="pb-2 font-medium">{{ 'bidding.dashboard.strategy' | translate }}</th>
                    <th class="pb-2 text-right font-medium">{{ 'bidding.dashboard.drr' | translate }}</th>
                    <th class="pb-2 text-right font-medium">{{ 'bidding.dashboard.bid' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of d.topHighDrr; track item.marketplaceOfferId) {
                    <tr class="border-b border-[var(--border-subtle)] last:border-0">
                      <td class="py-1.5 font-mono text-[var(--text-primary)]">{{ item.marketplaceSku }}</td>
                      <td class="py-1.5 text-[var(--text-secondary)]">
                        {{ 'bidding.policies.strategy.' + item.strategyType | translate }}
                      </td>
                      <td class="py-1.5 text-right font-mono text-[var(--status-error)]">
                        {{ item.drrPct | number:'1.1-1' }}%
                      </td>
                      <td class="py-1.5 text-right font-mono text-[var(--text-primary)]">
                        {{ item.currentBid | number }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>

          <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <h3 class="mb-3 text-[length:var(--text-base)] font-medium text-[var(--text-primary)]">
              {{ 'bidding.dashboard.top_improved' | translate }}
            </h3>
            @if (d.topImproved.length === 0) {
              <div class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'bidding.dashboard.no_data' | translate }}
              </div>
            } @else {
              <table class="w-full text-[length:var(--text-sm)]">
                <thead>
                  <tr class="border-b border-[var(--border-subtle)] text-left text-[var(--text-secondary)]">
                    <th class="pb-2 font-medium">SKU</th>
                    <th class="pb-2 font-medium">{{ 'bidding.dashboard.strategy' | translate }}</th>
                    <th class="pb-2 text-right font-medium">{{ 'bidding.dashboard.drr' | translate }}</th>
                    <th class="pb-2 text-right font-medium">{{ 'bidding.dashboard.bid' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of d.topImproved; track item.marketplaceOfferId) {
                    <tr class="border-b border-[var(--border-subtle)] last:border-0">
                      <td class="py-1.5 font-mono text-[var(--text-primary)]">{{ item.marketplaceSku }}</td>
                      <td class="py-1.5 text-[var(--text-secondary)]">
                        {{ 'bidding.policies.strategy.' + item.strategyType | translate }}
                      </td>
                      <td class="py-1.5 text-right font-mono text-[var(--finance-positive)]">
                        {{ item.drrPct | number:'1.1-1' }}%
                      </td>
                      <td class="py-1.5 text-right font-mono text-[var(--text-primary)]">
                        {{ item.currentBid | number }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class BiddingDashboardPageComponent {

  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly dashboardQuery = injectQuery(() => ({
    queryKey: ['bidding-dashboard', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.getDashboard(this.wsStore.currentWorkspaceId()!),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  decisionEntries(map: Record<string, number>): { key: string; value: number }[] {
    return Object.entries(map).map(([key, value]) => ({ key, value }));
  }
}
