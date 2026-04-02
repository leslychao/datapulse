import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, X, ExternalLink } from 'lucide-angular';

import { PricingApiService } from '@core/api/pricing-api.service';
import { DecisionOutcome } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ExplanationBlockComponent } from '@shared/components/explanation-block.component';
import {
  formatDateTime,
  formatMoney,
  formatPercent,
  financeColor,
} from '@shared/utils/format.utils';

const DECISION_COLOR: Record<DecisionOutcome, string> = {
  CHANGE: 'var(--status-success)',
  SKIP: 'var(--status-warning)',
  HOLD: 'var(--status-neutral)',
};

@Component({
  selector: 'dp-decision-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, RouterLink, ExplanationBlockComponent],
  template: `
    @if (decisionQuery.isPending()) {
      <div class="flex h-full items-center justify-center p-6">
        <span
          class="dp-spinner inline-block h-8 w-8 rounded-full border-2 border-[var(--border-default)]"
          style="border-top-color: var(--accent-primary)"
        ></span>
      </div>
    } @else if (decisionQuery.isError() || !decisionQuery.data()) {
      <div class="p-4 text-[length:var(--text-sm)] text-[var(--status-error)]">
        {{ 'pricing.decision_panel.load_error' | translate }}
      </div>
    } @else {
      @let d = decisionQuery.data()!;
      <div class="flex h-full flex-col">
        <!-- Header -->
        <div class="shrink-0 border-b border-[var(--border-default)] px-4 py-3">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <p class="text-[length:var(--text-xs)] font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ 'pricing.decision_panel.title' | translate:{ id: d.id } }}
              </p>
              <h3 class="mt-1 line-clamp-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
                {{ d.offerName }}
              </h3>
              <div class="mt-2 flex flex-wrap gap-2">
                <span
                  class="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium"
                  [style.color]="decisionColor(d.decisionType)"
                  [style.background-color]="decisionBg(d.decisionType)"
                >
                  <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="decisionColor(d.decisionType)"></span>
                  {{ ('pricing.decisions.type.' + d.decisionType) | translate }}
                </span>
              </div>
            </div>
            <button
              type="button"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-[var(--radius-sm)] text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
              (click)="closePanel()"
              [attr.aria-label]="'detail_panel.close' | translate"
            >
              <lucide-icon [img]="closeIcon" [size]="16" />
            </button>
          </div>
        </div>

        <!-- Content -->
        <div class="flex-1 space-y-4 overflow-auto px-4 py-3">
          <!-- Meta info -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.decision_panel.section.meta' | translate }}
            </h4>
            <dl class="space-y-1.5 text-[length:var(--text-sm)]">
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.decision_panel.sku' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ d.sellerSku }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.decision_panel.connection' | translate }}</dt>
                <dd class="text-right text-[var(--text-primary)]">{{ d.connectionName }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.decision_panel.policy' | translate }}</dt>
                <dd class="text-right text-[var(--text-primary)]">{{ d.policyName }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.decision_panel.strategy' | translate }}</dt>
                <dd class="text-right text-[var(--text-primary)]">
                  {{ ('pricing.policies.strategy.' + d.strategyType) | translate }}
                </dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'pricing.decision_panel.created_at' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ fmtDate(d.createdAt) }}</dd>
              </div>
            </dl>
          </section>

          <!-- Price change -->
          @if (d.decisionType === 'CHANGE' && d.currentPrice != null && d.targetPrice != null) {
            <section>
              <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
                {{ 'pricing.decision_panel.section.price' | translate }}
              </h4>
              <div class="flex items-center gap-3 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3">
                <span class="font-mono text-lg text-[var(--text-secondary)] line-through">{{ fmtMoney(d.currentPrice) }}</span>
                <span class="text-[var(--text-tertiary)]">→</span>
                <span class="font-mono text-lg font-semibold text-[var(--text-primary)]">{{ fmtMoney(d.targetPrice) }}</span>
                @if (d.changePct != null) {
                  <span class="font-mono text-sm" [style.color]="fColor(d.changePct)">
                    {{ fmtPctSigned(d.changePct) }}
                  </span>
                }
              </div>
            </section>
          }

          @if (d.skipReason) {
            <section>
              <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
                {{ 'pricing.decision_panel.section.skip_reason' | translate }}
              </h4>
              <p class="rounded-[var(--radius-md)] border border-[var(--status-warning)]/30 bg-[color-mix(in_srgb,var(--status-warning)_8%,transparent)] px-3 py-2 text-sm text-[var(--text-primary)]">
                {{ d.skipReason | translate }}
              </p>
            </section>
          }

          <!-- Explanation -->
          @if (d.explanationSummary) {
            <section>
              <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
                {{ 'pricing.decision_panel.section.explanation' | translate }}
              </h4>
              <dp-explanation-block [text]="d.explanationSummary" />
            </section>
          }

          <!-- Guards -->
          @if (d.guardsEvaluated?.length) {
            <section>
              <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
                {{ 'pricing.decision_panel.section.guards' | translate }}
              </h4>
              <div class="space-y-1">
                @for (g of d.guardsEvaluated; track g.guardName) {
                  <div class="flex items-center gap-2 text-[length:var(--text-sm)]">
                    <span class="inline-block h-2 w-2 rounded-full"
                      [style.background-color]="g.passed ? 'var(--status-success)' : 'var(--status-error)'"></span>
                    <span class="text-[var(--text-primary)]">{{ g.guardName }}</span>
                    @if (g.details) {
                      <span class="text-[var(--text-tertiary)]">— {{ g.details | translate }}</span>
                    }
                  </div>
                }
              </div>
            </section>
          }

          <!-- Links -->
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'pricing.decision_panel.section.links' | translate }}
            </h4>
            <div class="flex flex-col gap-2">
              <a
                [routerLink]="runRoute(d.pricingRunId)"
                class="inline-flex items-center gap-2 text-[length:var(--text-sm)] text-[var(--accent-primary)] hover:underline"
              >
                <lucide-icon [img]="extLinkIcon" [size]="14" />
                {{ 'pricing.decision_panel.link_run' | translate:{ id: d.pricingRunId } }}
              </a>
              <a
                [routerLink]="detailRoute(d.id)"
                class="inline-flex items-center gap-2 text-[length:var(--text-sm)] text-[var(--accent-primary)] hover:underline"
              >
                <lucide-icon [img]="extLinkIcon" [size]="14" />
                {{ 'pricing.decision_panel.link_full_detail' | translate }}
              </a>
            </div>
          </section>
        </div>
      </div>
    }
  `,
})
export class DecisionDetailPanelComponent {
  protected readonly panelService = inject(DetailPanelService);
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);

  readonly closeIcon = X;
  readonly extLinkIcon = ExternalLink;

  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId() ?? 0);

  readonly decisionQuery = injectQuery(() => ({
    queryKey: ['pricing-decision', 'panel', this.panelService.entityId()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.getDecisionDetail(
          this.workspaceId(),
          this.panelService.entityId()!,
        ),
      ),
    enabled:
      this.panelService.isOpen() &&
      this.panelService.entityType() === 'pricing-decision' &&
      this.panelService.entityId() != null,
  }));

  decisionColor(type: DecisionOutcome): string {
    return DECISION_COLOR[type] ?? 'var(--text-tertiary)';
  }

  decisionBg(type: DecisionOutcome): string {
    return `color-mix(in srgb, ${this.decisionColor(type)} 12%, transparent)`;
  }

  runRoute(runId: number): string[] {
    return ['/workspace', String(this.workspaceId()), 'pricing', 'runs', String(runId)];
  }

  detailRoute(decisionId: number): string[] {
    return ['/workspace', String(this.workspaceId()), 'pricing', 'decisions', String(decisionId)];
  }

  fmtDate(iso: string): string {
    return formatDateTime(iso);
  }

  fmtMoney(value: number): string {
    return formatMoney(value, 0);
  }

  fmtPctSigned(value: number): string {
    return formatPercent(value, 1, true);
  }

  fColor(value: number): string {
    return financeColor(value);
  }

  closePanel(): void {
    this.panelService.close();
  }
}
