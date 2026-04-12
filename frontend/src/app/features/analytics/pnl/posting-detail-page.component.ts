import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { PostingEntry } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { formatMoney } from '@shared/utils/format.utils';

interface MeasureColumn {
  labelKey: string;
  field: keyof PostingEntry;
  isCost?: boolean;
}

const MEASURE_COLUMNS: MeasureColumn[] = [
  { labelKey: 'analytics.pnl.col.revenue', field: 'revenueAmount' },
  { labelKey: 'analytics.pnl.col.commission', field: 'marketplaceCommissionAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.acquiring', field: 'acquiringCommissionAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.logistics', field: 'logisticsCostAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.storage', field: 'storageCostAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.penalties', field: 'penaltiesAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.acceptance', field: 'acceptanceCostAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.marketing', field: 'marketingCostAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.other_charges', field: 'otherMarketplaceChargesAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.compensation', field: 'compensationAmount' },
  { labelKey: 'analytics.pnl.col.refunds', field: 'refundAmount', isCost: true },
  { labelKey: 'analytics.pnl.col.payout', field: 'netPayout' },
];

@Component({
  selector: 'dp-posting-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, MarketplaceBadgeComponent],
  templateUrl: './posting-detail-page.component.html',
})
export class PostingDetailPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly t = inject(TranslateService);

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

  goBack(): void {
    this.router.navigate([
      '/workspace', this.wsStore.currentWorkspaceId(),
      'analytics', 'pnl', 'by-posting',
    ]);
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  moneyColorClass(value: number | null, isCost = false): string {
    if (value == null || value === 0) return 'text-[var(--finance-zero)]';
    if (isCost) return 'text-[var(--text-primary)]';
    if (value > 0) return 'text-[var(--finance-positive)]';
    return 'text-[var(--finance-negative)]';
  }

  residualColorClass(value: number): string {
    if (value !== 0) return 'text-[var(--status-warning)]';
    return 'text-[var(--finance-zero)]';
  }

  entryTypeLabel(type: string): string {
    const key = `analytics.pnl.entry_type.${type}`;
    const translated = this.t.instant(key);
    return translated === key ? type : translated;
  }

  entryMeasure(entry: PostingEntry, field: string): number {
    return entry[field as keyof PostingEntry] as number;
  }

}
