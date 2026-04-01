import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, X, Link2, Plug } from 'lucide-angular';

import { AlertApiService } from '@core/api/alert-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { formatDateTime } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-alert-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, RouterLink],
  template: `
    @if (alertQuery.isPending()) {
      <div class="flex h-full items-center justify-center p-6">
        <span
          class="dp-spinner inline-block h-8 w-8 rounded-full border-2 border-[var(--border-default)]"
          style="border-top-color: var(--accent-primary)"
        ></span>
      </div>
    } @else if (alertQuery.isError() || !alertQuery.data()) {
      <div class="p-4 text-[length:var(--text-sm)] text-[var(--status-error)]">
        {{ 'alerts.detail.load_error' | translate }}
      </div>
    } @else {
      @let alert = alertQuery.data();
      @if (alert) {
      <div class="flex h-full flex-col">
        <div class="shrink-0 border-b border-[var(--border-default)] px-4 py-3">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <p class="text-[length:var(--text-xs)] font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ ('alerts.rule_type.' + alert.ruleType) | translate }}
              </p>
              <h3 class="mt-1 line-clamp-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
                {{ alert.title }}
              </h3>
              <div class="mt-2 flex flex-wrap gap-2">
                <span
                  class="inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium"
                  [style.color]="severityFg(alert.severity)"
                  [style.background-color]="severityBg(alert.severity)"
                >
                  {{ ('alerts.severity.' + alert.severity) | translate }}
                </span>
                <span
                  class="inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium"
                  style="color: var(--text-primary); background-color: color-mix(in srgb, var(--text-tertiary) 12%, transparent)"
                >
                  {{ ('alerts.status.' + alert.status) | translate }}
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

        <div class="flex-1 space-y-4 overflow-auto px-4 py-3">
          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'alerts.detail.general' | translate }}
            </h4>
            <dl class="space-y-1.5 text-[length:var(--text-sm)]">
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'alerts.detail.connection' | translate }}</dt>
                <dd class="text-right text-[var(--text-primary)]">{{ alert.connectionName ?? '—' }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'alerts.detail.opened_at' | translate }}</dt>
                <dd class="font-mono text-right text-[var(--text-primary)]">{{ formatDt(alert.openedAt) }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-[var(--text-secondary)]">{{ 'alerts.detail.blocks_automation' | translate }}</dt>
                <dd class="text-right text-[var(--text-primary)]">
                  {{ alert.blocksAutomation ? ('alerts.detail.yes' | translate) : ('alerts.detail.no' | translate) }}
                </dd>
              </div>
            </dl>
          </section>

          @if (detailEntries().length > 0) {
            <section>
              <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
                {{ 'alerts.detail.context' | translate }}
              </h4>
              <dl class="space-y-1 rounded-[var(--radius-md)] border border-[var(--border-subtle)] bg-[var(--bg-secondary)] p-3 font-mono text-[length:var(--text-xs)] text-[var(--text-primary)]">
                @for (kv of detailEntries(); track kv[0]) {
                  <div class="flex gap-2 break-all">
                    <dt class="shrink-0 text-[var(--text-tertiary)]">{{ kv[0] }}</dt>
                    <dd class="min-w-0 flex-1">{{ kv[1] }}</dd>
                  </div>
                }
              </dl>
            </section>
          }

          <section>
            <h4 class="mb-2 text-[length:var(--text-xs)] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'alerts.detail.links' | translate }}
            </h4>
            <div class="flex flex-col gap-2">
              @if (alert.connectionId != null) {
                <a
                  [routerLink]="['/workspace', workspaceId(), 'settings', 'connections']"
                  class="inline-flex items-center gap-2 text-[length:var(--text-sm)] text-[var(--accent-primary)] hover:underline"
                >
                  <lucide-icon [img]="plugIcon" [size]="14" />
                  {{ 'alerts.detail.link_connections' | translate }}
                </a>
              }
              @if (alert.ruleType === 'ACTION_FAILED' || alert.ruleType === 'STUCK_STATE') {
                <a
                  [routerLink]="['/workspace', workspaceId(), 'execution', 'actions']"
                  class="inline-flex items-center gap-2 text-[length:var(--text-sm)] text-[var(--accent-primary)] hover:underline"
                >
                  <lucide-icon [img]="linkIcon" [size]="14" />
                  {{ 'alerts.detail.link_execution' | translate }}
                </a>
              }
            </div>
          </section>
        </div>

        <div class="shrink-0 border-t border-[var(--border-default)] px-4 py-3">
          @if (alert.status === 'OPEN') {
            <button
              type="button"
              class="w-full cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-[length:var(--text-sm)] font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:opacity-50"
              [disabled]="ackMutation.isPending()"
              (click)="ackMutation.mutate(alert.id)"
            >
              {{ 'alerts.detail.acknowledge' | translate }}
            </button>
          } @else if (alert.status === 'ACKNOWLEDGED') {
            <button
              type="button"
              class="w-full cursor-pointer rounded-[var(--radius-md)] bg-[var(--status-success)] px-4 py-2 text-[length:var(--text-sm)] font-medium text-white transition-colors hover:opacity-90 disabled:opacity-50"
              [disabled]="resolveMutation.isPending()"
              (click)="resolveMutation.mutate(alert.id)"
            >
              {{ 'alerts.detail.resolve' | translate }}
            </button>
          }
        </div>
      </div>
      }
    }
  `,
})
export class AlertDetailPanelComponent {
  protected readonly panelService = inject(DetailPanelService);
  private readonly alertApi = inject(AlertApiService);
  private readonly router = inject(Router);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  readonly closeIcon = X;
  readonly linkIcon = Link2;
  readonly plugIcon = Plug;

  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId() ?? 0);

  readonly alertQuery = injectQuery(() => ({
    queryKey: ['alerts', 'detail', this.panelService.entityId()],
    queryFn: () => lastValueFrom(this.alertApi.getAlert(this.panelService.entityId()!)),
    enabled:
      this.panelService.isOpen() &&
      this.panelService.entityType() === 'alert' &&
      this.panelService.entityId() != null,
  }));

  readonly ackMutation = injectMutation(() => ({
    mutationFn: (id: number) => lastValueFrom(this.alertApi.acknowledge(id)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('alerts.mutation.ack_success'));
      this.queryClient.invalidateQueries({ queryKey: ['alerts'] });
      this.alertQuery.refetch();
    },
    onError: () => this.toast.error(this.translate.instant('alerts.mutation.ack_error')),
  }));

  readonly resolveMutation = injectMutation(() => ({
    mutationFn: (id: number) => lastValueFrom(this.alertApi.resolve(id)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('alerts.mutation.resolve_success'));
      this.queryClient.invalidateQueries({ queryKey: ['alerts'] });
      this.alertQuery.refetch();
    },
    onError: () => this.toast.error(this.translate.instant('alerts.mutation.resolve_error')),
  }));

  readonly detailEntries = computed(() => {
    const d = this.alertQuery.data()?.details;
    if (!d || typeof d !== 'object') {
      return [] as [string, string][];
    }
    return Object.entries(d).map(([k, v]): [string, string] => [
      k,
      typeof v === 'object' ? JSON.stringify(v) : String(v),
    ]);
  });

  severityFg(sev: string): string {
    if (sev === 'CRITICAL') {
      return 'var(--status-error)';
    }
    if (sev === 'WARNING') {
      return 'var(--status-warning)';
    }
    return 'var(--status-info)';
  }

  severityBg(sev: string): string {
    const fg = this.severityFg(sev);
    return `color-mix(in srgb, ${fg} 12%, transparent)`;
  }

  formatDt(iso: string): string {
    return formatDateTime(iso);
  }

  closePanel(): void {
    const ws = this.wsStore.currentWorkspaceId();
    if (ws != null) {
      this.router.navigate(['/workspace', ws, 'alerts', 'events']);
    }
    this.panelService.close();
  }
}
