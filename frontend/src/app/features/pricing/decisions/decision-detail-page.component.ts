import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { PricingDecisionDetail } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ExplanationBlockComponent } from '@shared/components/explanation-block.component';
import { LoadingSkeletonComponent } from '@shared/components/loading-skeleton.component';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';

const DECISION_TYPE_COLOR: Record<string, string> = {
  CHANGE: 'success',
  SKIP: 'warning',
  HOLD: 'neutral',
};

@Component({
  selector: 'dp-decision-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-1 flex-col min-h-0 overflow-y-auto' },
  imports: [
    TranslatePipe,
    EmptyStateComponent,
    ExplanationBlockComponent,
    LoadingSkeletonComponent,
  ],
  templateUrl: './decision-detail-page.component.html',
})
export class DecisionDetailPageComponent {
  readonly decisionId = input.required<number>();

  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly policyExpanded = signal(false);

  readonly decisionQuery = injectQuery(() => ({
    queryKey: ['decision-detail', this.wsStore.currentWorkspaceId(), this.decisionId()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.getDecisionDetail(
          this.wsStore.currentWorkspaceId()!,
          this.decisionId(),
        ),
      ).catch((err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.toast.error(
            this.translate.instant('pricing.decisions.detail.not_found'),
          );
          const wsId = this.wsStore.currentWorkspaceId();
          if (wsId) {
            void this.router.navigate(['/workspace', wsId, 'pricing', 'decisions']);
          }
        }
        throw err;
      }),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly decision = computed<PricingDecisionDetail | null>(
    () => this.decisionQuery.data() ?? null,
  );

  readonly decisionTypeCssVar = computed(() => {
    const dt = this.decision()?.decisionType ?? '';
    const color = DECISION_TYPE_COLOR[dt] ?? 'neutral';
    return `var(--status-${color})`;
  });

  readonly signalRows = computed(() => {
    const snap = this.decision()?.signalSnapshot;
    if (!snap) {
      return [];
    }
    return Object.keys(snap)
      .sort()
      .map((key) => ({
        key,
        label: this.signalKeyLabel(key),
        value: this.formatUnknownValue(snap[key]),
      }));
  });

  readonly constraintsRows = computed(() => this.decision()?.constraintsApplied ?? []);

  readonly guardsRows = computed(() => this.decision()?.guardsEvaluated ?? []);

  readonly policyJson = computed(() => {
    const snap = this.decision()?.policySnapshot;
    if (!snap) {
      return '';
    }
    return JSON.stringify(snap, null, 2);
  });

  readonly hasPolicySnapshot = computed(() => this.decision()?.policySnapshot != null);

  togglePolicySnapshot(): void {
    this.policyExpanded.update((v) => !v);
  }

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) {
      return;
    }
    void this.router.navigate(['/workspace', wsId, 'pricing', 'decisions']);
  }

  navigateToRun(runId: number): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) {
      return;
    }
    void this.router.navigate(['/workspace', wsId, 'pricing', 'runs', runId]);
  }

  formatPrice(value: number | null | undefined): string {
    return formatMoney(value, 0);
  }

  formatCreatedAt(iso: string | null | undefined): string {
    return formatDateTime(iso, 'full');
  }

  formatChangePct(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    const abs = Math.abs(value).toFixed(1).replace('.', ',');
    if (value > 0) {
      return `↑ ${abs}%`;
    }
    if (value < 0) {
      return `↓ ${abs}%`;
    }
    return `→ 0%`;
  }

  changePctClass(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return 'font-mono text-[var(--text-primary)]';
    }
    if (value > 0) {
      return 'font-mono text-[var(--finance-positive)]';
    }
    if (value < 0) {
      return 'font-mono text-[var(--finance-negative)]';
    }
    return 'font-mono text-[var(--finance-zero)]';
  }

  guardDetails(details: string | null): string {
    if (!details) {
      return '—';
    }
    return this.translate.instant(details);
  }

  private signalKeyLabel(key: string): string {
    const tk = `pricing.decisions.signal.${key}`;
    const t = this.translate.instant(tk);
    return t === tk ? key : t;
  }

  private formatUnknownValue(value: unknown): string {
    if (value === null || value === undefined) {
      return '—';
    }
    if (typeof value === 'object') {
      return JSON.stringify(value);
    }
    return String(value);
  }
}
