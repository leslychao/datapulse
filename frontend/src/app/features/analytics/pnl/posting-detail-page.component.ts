import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { PostingEntry } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { formatMoney } from '@shared/utils/format.utils';

interface MeasureColumn {
  labelKey: string;
  field: keyof PostingEntry;
}

const MEASURE_COLUMNS: MeasureColumn[] = [
  { labelKey: 'analytics.pnl.col.revenue', field: 'revenueAmount' },
  { labelKey: 'analytics.pnl.col.commission', field: 'marketplaceCommissionAmount' },
  { labelKey: 'analytics.pnl.col.acquiring', field: 'acquiringCommissionAmount' },
  { labelKey: 'analytics.pnl.col.logistics', field: 'logisticsCostAmount' },
  { labelKey: 'analytics.pnl.col.storage', field: 'storageCostAmount' },
  { labelKey: 'analytics.pnl.col.penalties', field: 'penaltiesAmount' },
  { labelKey: 'analytics.pnl.col.acceptance', field: 'acceptanceCostAmount' },
  { labelKey: 'analytics.pnl.col.marketing', field: 'marketingCostAmount' },
  { labelKey: 'analytics.pnl.col.other_charges', field: 'otherMarketplaceChargesAmount' },
  { labelKey: 'analytics.pnl.col.compensation', field: 'compensationAmount' },
  { labelKey: 'analytics.pnl.col.refunds', field: 'refundAmount' },
  { labelKey: 'analytics.pnl.col.payout', field: 'netPayout' },
];

@Component({
  selector: 'dp-posting-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  templateUrl: './posting-detail-page.component.html',
})
export class PostingDetailPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);

  readonly postingId = input.required<string>();
  readonly measureColumns = MEASURE_COLUMNS;

  readonly detailQuery = injectQuery(() => ({
    queryKey: ['analytics', 'posting-detail', this.wsStore.currentWorkspaceId(), this.postingId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getPostingDetail(
          this.wsStore.currentWorkspaceId()!,
          this.postingId(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.postingId(),
  }));

  readonly totals = computed(() => {
    const entries = this.detailQuery.data()?.entries ?? [];
    const result: Record<string, number> = {};
    for (const col of MEASURE_COLUMNS) {
      result[col.field] = entries.reduce(
        (sum, e) => sum + (e[col.field] as number),
        0,
      );
    }
    return result;
  });

  openProvenance(entryId: number): void {
    lastValueFrom(
      this.analyticsApi.getProvenanceRawUrl(
        this.wsStore.currentWorkspaceId()!,
        entryId,
      ),
    ).then((result) => {
      window.open(result.url, '_blank');
    });
  }

  goBack(): void {
    this.router.navigate([
      '/workspace', this.wsStore.currentWorkspaceId(),
      'analytics', 'pnl', 'by-posting',
    ]);
  }

  formatMoney(value: number | null): string {
    if (value === 0) return '—';
    return formatMoney(value, 0);
  }

  moneyColorClass(value: number | null): string {
    if (value != null && value > 0) return 'text-[var(--finance-positive)]';
    if (value != null && value < 0) return 'text-[var(--finance-negative)]';
    return 'text-[var(--finance-zero)]';
  }

  residualColorClass(value: number): string {
    if (value !== 0) return 'text-[var(--status-warning)]';
    return 'text-[var(--finance-zero)]';
  }

  entryMeasure(entry: PostingEntry, field: string): number {
    return entry[field as keyof PostingEntry] as number;
  }

  platformBadge(platform: string): string {
    if (platform === 'WB') return 'bg-[var(--mp-wb-bg)] text-[var(--mp-wb)]';
    if (platform === 'OZON') return 'bg-[var(--mp-ozon-bg)] text-[var(--mp-ozon)]';
    return 'bg-[var(--status-neutral-bg)] text-[var(--status-neutral)]';
  }
}
