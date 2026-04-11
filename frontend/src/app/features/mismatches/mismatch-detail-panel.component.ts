import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectMutation,
  injectQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  X,
  RefreshCw,
  Check,
  Ban,
  ArrowUpRight,
  CircleCheck,
  ChevronLeft,
  ChevronRight,
} from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { AlertApiService } from '@core/api/alert-api.service';
import { MismatchApiService } from '@core/api/mismatch-api.service';
import { MarketplaceType, MismatchDetail, MismatchStatus } from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { MismatchResolveModalComponent } from './mismatch-resolve-modal.component';

type DetailTab = 'comparison' | 'timeline' | 'action';

const TERMINAL_STATUSES: MismatchStatus[] = ['RESOLVED', 'AUTO_RESOLVED', 'IGNORED'];

function mp(t: string): MarketplaceType {
  return t === 'OZON' ? 'OZON' : 'WB';
}

function stColor(st: MismatchStatus): 'success' | 'error' | 'warning' | 'info' | 'neutral' {
  if (st === 'ACTIVE') return 'error';
  if (st === 'RESOLVED' || st === 'AUTO_RESOLVED') return 'success';
  if (st === 'ACKNOWLEDGED') return 'warning';
  return 'neutral';
}

@Component({
  selector: 'dp-mismatch-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgClass,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    MarketplaceBadgeComponent,
    StatusBadgeComponent,
    ConfirmationModalComponent,
    DateFormatPipe,
    MismatchResolveModalComponent,
  ],
  template: `
    <aside
      class="relative flex h-full shrink-0 flex-col border-l border-[var(--border-default)]
             bg-[var(--bg-primary)] shadow-[var(--shadow-md)]"
      [style.width.px]="panelWidth()"
    >
      <!-- Drag handle -->
      <div
        class="absolute left-0 top-0 z-20 h-full w-1.5 cursor-col-resize
               transition-colors hover:bg-[var(--accent-primary)]"
        (pointerdown)="onDragStart($event)"
      ></div>

      @if (detailQuery.data()?.severity === 'CRITICAL') {
        <div class="h-1 w-full bg-[var(--status-error)]"></div>
      } @else if (detailQuery.data()?.severity === 'WARNING') {
        <div class="h-1 w-full bg-[var(--status-warning)]"></div>
      }

      <!-- Header with prev/next -->
      <div class="flex items-start justify-between gap-2 border-b border-[var(--border-default)] px-4 py-3">
        <div class="flex items-center gap-1">
          <button
            type="button"
            [disabled]="!prevId()"
            (click)="prevId() && navigated.emit(prevId()!)"
            class="cursor-pointer rounded-[var(--radius-md)] p-1 text-[var(--text-secondary)]
                   hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-30"
            [attr.aria-label]="'mismatches.detail.prev' | translate"
          >
            <lucide-icon [img]="PrevIcon" [size]="16" />
          </button>
          <button
            type="button"
            [disabled]="!nextId()"
            (click)="nextId() && navigated.emit(nextId()!)"
            class="cursor-pointer rounded-[var(--radius-md)] p-1 text-[var(--text-secondary)]
                   hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-30"
            [attr.aria-label]="'mismatches.detail.next' | translate"
          >
            <lucide-icon [img]="NextIcon" [size]="16" />
          </button>
        </div>

        <div class="min-w-0 flex-1">
          @if (detailQuery.isError()) {
            <div class="flex flex-col items-center gap-2 py-6 text-center">
              <span class="text-sm text-[var(--status-error)]">{{ 'mismatches.detail.error' | translate }}</span>
              <button type="button" (click)="detailQuery.refetch()" class="text-sm font-medium text-[var(--accent-primary)] hover:underline">
                {{ 'actions.retry' | translate }}
              </button>
            </div>
          } @else if (detailQuery.isPending()) {
            <div class="dp-shimmer mb-2 h-4 w-3/4 rounded-[var(--radius-sm)]"></div>
            <div class="dp-shimmer h-3 w-1/2 rounded-[var(--radius-sm)]"></div>
          } @else {
            @if (detailQuery.data(); as d) {
              <h2 class="truncate text-base font-semibold text-[var(--text-primary)]">{{ d.offer.offerName }}</h2>
              <p class="mt-0.5 font-mono text-[var(--text-xs)] text-[var(--text-secondary)]">{{ d.offer.skuCode }}</p>
              <div class="mt-2 flex flex-wrap items-center gap-2">
                <dp-marketplace-badge [type]="mpType(d)" />
                <dp-status-badge [label]="'mismatches.type.' + d.type | translate" [color]="'info'" [dot]="true" />
                <dp-status-badge [label]="'mismatches.severity.' + d.severity | translate" [color]="d.severity === 'CRITICAL' ? 'error' : 'warning'" [dot]="true" />
                <dp-status-badge [label]="'mismatches.status.' + d.status | translate" [color]="badgeForStatus(d.status)" [dot]="true" />
              </div>
              <p class="mt-2 text-[var(--text-xs)] text-[var(--text-secondary)]">{{ 'mismatches.detail.detected' | translate }}: {{ d.detectedAt | dpDateFormat }}</p>
            }
          }
        </div>

        <button type="button" (click)="closed.emit()" class="cursor-pointer rounded-[var(--radius-md)] p-1.5 text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]" [attr.aria-label]="'mismatches.detail.close' | translate">
          <lucide-icon [img]="CloseIcon" [size]="18" />
        </button>
      </div>

      <!-- Tabs -->
      <div class="flex gap-1 border-b border-[var(--border-default)] px-2 pt-2">
        <button type="button" (click)="activeTab.set('comparison')" [ngClass]="tabCls('comparison')">{{ 'mismatches.tabs.comparison' | translate }}</button>
        <button type="button" (click)="activeTab.set('timeline')" [ngClass]="tabCls('timeline')">{{ 'mismatches.tabs.timeline' | translate }}</button>
        <button type="button" (click)="activeTab.set('action')" [ngClass]="tabCls('action')">{{ 'mismatches.tabs.action' | translate }}</button>
      </div>

      <!-- Content -->
      <div class="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        @if (detailQuery.data(); as d) {
          @if (activeTab() === 'comparison') {
            <div class="overflow-x-auto rounded-[var(--radius-md)] border border-[var(--border-default)]">
              <table class="w-full text-sm">
                <thead class="bg-[var(--bg-secondary)] text-left text-[var(--text-secondary)]"><tr><th class="px-3 py-2">{{ 'mismatches.detail.field' | translate }}</th><th class="px-3 py-2">{{ 'mismatches.detail.expected' | translate }}</th><th class="px-3 py-2">{{ 'mismatches.detail.actual' | translate }}</th></tr></thead>
                <tbody class="divide-y divide-[var(--border-subtle)]">
                  <tr>
                    <td class="px-3 py-2 text-[var(--text-secondary)]">{{ 'mismatches.detail.value_row' | translate }}</td>
                    <td class="max-w-[140px] break-words px-3 py-2 font-mono" [ngClass]="cellExpected(d)">{{ d.expectedValue }}</td>
                    <td class="max-w-[140px] break-words px-3 py-2 font-mono" [ngClass]="cellActual(d)">
                      {{ d.actualValue }}
                      @if (d.expectedValue !== d.actualValue && d.deltaPct != null) {
                        <span class="ml-1 text-[11px] font-medium text-[var(--status-error)]">
                          ({{ d.deltaPct > 0 ? '+' : '' }}{{ d.deltaPct }}%)
                        </span>
                      }
                    </td>
                  </tr>
                  <tr>
                    <td class="px-3 py-2 text-[var(--text-secondary)]">{{ 'mismatches.detail.source' | translate }}</td>
                    <td class="px-3 py-2">{{ d.expectedSource }}</td>
                    <td class="px-3 py-2">{{ d.actualSource }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            @if (d.thresholds) {
              <div class="mt-3 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] px-3 py-2 text-[var(--text-xs)] text-[var(--text-secondary)]">
                <span class="font-medium">WARNING:</span> > {{ d.thresholds.warningPct }}%
                <span class="ml-3 font-medium">CRITICAL:</span> > {{ d.thresholds.criticalPct }}%
                @if (d.deltaPct != null) {
                  <span class="ml-3">\u2192 {{ d.deltaPct }}% ({{ d.severity }})</span>
                }
              </div>
            }
          } @else if (activeTab() === 'timeline') {
            @if (d.timeline.length === 0) {
              <p class="text-sm text-[var(--text-secondary)]">{{ 'mismatches.detail.timeline_empty' | translate }}</p>
            } @else {
              <ul class="space-y-3 border-l border-[var(--border-default)] pl-3">
                @for (ev of d.timeline; track ev.timestamp + ev.eventType) {
                  <li class="relative pl-2">
                    <span class="absolute -left-[5px] top-1.5 h-2 w-2 rounded-full" [style.background-color]="dot(ev.eventType)"></span>
                    <p class="text-[var(--text-xs)] text-[var(--text-tertiary)]">{{ ev.timestamp | dpDateFormat }}</p>
                    <p class="text-sm text-[var(--text-primary)]">{{ ev.description }}</p>
                    <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">{{ ev.actor }}</p>
                  </li>
                }
              </ul>
            }
          } @else if (!d.relatedAction) {
            <p class="text-sm text-[var(--text-secondary)]">{{ 'mismatches.detail.no_action' | translate }}</p>
          } @else {
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-2"><dt class="text-[var(--text-secondary)]">{{ 'mismatches.detail.action_id' | translate }}</dt><dd class="font-mono">{{ d.relatedAction.actionId }}</dd></div>
              <div class="flex justify-between gap-2"><dt class="text-[var(--text-secondary)]">{{ 'mismatches.detail.action_status' | translate }}</dt><dd>{{ d.relatedAction.status }}</dd></div>
              <div class="flex justify-between gap-2"><dt class="text-[var(--text-secondary)]">{{ 'mismatches.detail.target_price' | translate }}</dt><dd class="font-mono">{{ d.relatedAction.targetPrice }}</dd></div>
              <div class="flex justify-between gap-2"><dt class="text-[var(--text-secondary)]">{{ 'mismatches.detail.executed_at' | translate }}</dt><dd>{{ d.relatedAction.executedAt | dpDateFormat }}</dd></div>
              <div class="flex justify-between gap-2"><dt class="text-[var(--text-secondary)]">{{ 'mismatches.detail.reconciliation' | translate }}</dt><dd>{{ d.relatedAction.reconciliationSource }}</dd></div>
            </dl>
            <div class="mt-3">
              <a class="inline-flex items-center gap-1 text-sm font-medium text-[var(--accent-primary)] hover:underline"
                 [routerLink]="['/workspace', ws.currentWorkspaceId(), 'pricing', 'price-actions', d.relatedAction.actionId]">
                {{ 'mismatches.detail.open_action' | translate }} \u2192
              </a>
            </div>
          }
        }
      </div>

      <!-- Actions footer -->
      @if (detailQuery.data(); as d) {
        @if (isTerminal(d.status)) {
          <div class="border-t border-[var(--border-default)] px-4 py-3">
            <div class="rounded-[var(--radius-md)] bg-[var(--bg-secondary)] px-3 py-2 text-sm text-[var(--text-secondary)]">
              <dp-status-badge [label]="'mismatches.status.' + d.status | translate" [color]="badgeForStatus(d.status)" [dot]="true" />
              @if (d.resolvedAt) {
                <span class="ml-2 text-[var(--text-xs)]">{{ d.resolvedAt | dpDateFormat }}</span>
              }
              @if (d.resolvedBy) {
                <span class="ml-1 text-[var(--text-xs)]">\u00B7 {{ d.resolvedBy }}</span>
              }
              @if (d.resolution) {
                <p class="mt-1 text-[var(--text-xs)]">{{ 'mismatches.resolution.' + d.resolution | translate }}</p>
              }
              @if (d.resolutionNote) {
                <p class="mt-1 text-[var(--text-xs)] italic">{{ d.resolutionNote }}</p>
              }
            </div>
          </div>
        } @else {
          <div class="border-t border-[var(--border-default)] px-4 py-3">
            <div class="flex flex-wrap gap-2">
              @if (d.status === 'ACTIVE') {
                @if (rbac.canOperateMismatches()) {
                  <button type="button" (click)="ackMutation.mutate()" [disabled]="ackMutation.isPending()" class="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-primary-hover)] disabled:opacity-50"><lucide-icon [img]="CheckIcon" [size]="14" />{{ 'mismatches.detail.ack' | translate }}</button>
                }
                @if (rbac.canIgnoreMismatches()) {
                  <button type="button" (click)="showIgnoreModal.set(true)" class="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm hover:bg-[var(--bg-tertiary)]"><lucide-icon [img]="BanIcon" [size]="14" />{{ 'mismatches.detail.ignore' | translate }}</button>
                }
                @if (rbac.canOperateMismatches()) {
                  <button type="button" (click)="onEscalate()" [disabled]="escalateMutation.isPending()" class="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] border border-[var(--status-error)] px-3 py-1.5 text-sm text-[var(--status-error)] hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)] disabled:opacity-50"><lucide-icon [img]="EscalateIcon" [size]="14" />{{ 'mismatches.detail.escalate' | translate }}</button>
                }
              }
              @if (d.status === 'ACKNOWLEDGED') {
                @if (rbac.canOperateMismatches()) {
                  <button type="button" (click)="showResolveModal.set(true)" class="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-primary-hover)]"><lucide-icon [img]="ResolveIcon" [size]="14" />{{ 'mismatches.detail.resolve' | translate }}</button>
                }
                @if (rbac.canOperateMismatches() && d.relatedActionId) {
                  <button type="button" (click)="retryMutation.mutate()" [disabled]="retryMutation.isPending()" class="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm hover:bg-[var(--bg-tertiary)] disabled:opacity-50"><lucide-icon [img]="RefreshIcon" [size]="14" />{{ 'mismatches.detail.retry' | translate }}</button>
                }
                @if (rbac.canOperateMismatches()) {
                  <button type="button" (click)="onEscalate()" [disabled]="escalateMutation.isPending()" class="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] border border-[var(--status-error)] px-3 py-1.5 text-sm text-[var(--status-error)] hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)] disabled:opacity-50"><lucide-icon [img]="EscalateIcon" [size]="14" />{{ 'mismatches.detail.escalate' | translate }}</button>
                }
              }
            </div>
          </div>
        }
      }
    </aside>

    <dp-mismatch-resolve-modal [open]="showResolveModal()" (resolved)="onResolve($event)" (cancelled)="showResolveModal.set(false)" />
    <dp-confirmation-modal [open]="showIgnoreModal()" [title]="'mismatches.ignore.title' | translate" [message]="'mismatches.ignore.message' | translate" [confirmLabel]="'mismatches.ignore.confirm' | translate" [danger]="true" (confirmed)="onIgnoreConfirm()" (cancelled)="showIgnoreModal.set(false)" />
  `,
})
export class MismatchDetailPanelComponent {
  private readonly api = inject(MismatchApiService);
  private readonly actionApi = inject(ActionApiService);
  private readonly alertApi = inject(AlertApiService);
  protected readonly ws = inject(WorkspaceContextStore);
  private readonly detailPanel = inject(DetailPanelService);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly mismatchId = input.required<number>();
  readonly prevId = input<number | null>(null);
  readonly nextId = input<number | null>(null);
  readonly closed = output<void>();
  readonly navigated = output<number>();

  readonly CloseIcon = X;
  readonly RefreshIcon = RefreshCw;
  readonly CheckIcon = Check;
  readonly BanIcon = Ban;
  readonly EscalateIcon = ArrowUpRight;
  readonly ResolveIcon = CircleCheck;
  readonly PrevIcon = ChevronLeft;
  readonly NextIcon = ChevronRight;

  readonly activeTab = signal<DetailTab>('comparison');
  readonly showResolveModal = signal(false);
  readonly showIgnoreModal = signal(false);

  protected readonly panelWidth = computed(() => this.detailPanel.width());

  readonly detailQuery = injectQuery(() => ({
    queryKey: ['mismatch-detail', this.ws.currentWorkspaceId(), this.mismatchId()],
    queryFn: () =>
      lastValueFrom(this.api.getDetail(this.ws.currentWorkspaceId()!, this.mismatchId())),
    enabled: !!this.ws.currentWorkspaceId() && this.mismatchId() > 0,
  }));

  readonly ackMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.api.acknowledge(this.ws.currentWorkspaceId()!, this.mismatchId())),
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.ack'));
      this.invalidateAll();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly retryMutation = injectMutation(() => ({
    mutationFn: () => {
      const id = this.detailQuery.data()?.relatedActionId;
      if (id == null) throw new Error('no action');
      return lastValueFrom(this.actionApi.retryAction(this.ws.currentWorkspaceId()!, id, 'mismatch investigation'));
    },
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.retry'));
      this.invalidateAll();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly resolveMutation = injectMutation(() => ({
    mutationFn: (body: { resolution: string; note: string }) =>
      lastValueFrom(this.api.resolve(this.ws.currentWorkspaceId()!, this.mismatchId(), body)),
    onSuccess: () => {
      this.showResolveModal.set(false);
      this.toast.success(this.translate.instant('mismatches.toast.resolve'));
      this.invalidateAll();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly escalateMutation = injectMutation(() => ({
    mutationFn: () => {
      const d = this.detailQuery.data();
      if (!d) throw new Error('no data');
      return lastValueFrom(this.alertApi.createAlert({
        sourceType: 'MISMATCH',
        sourceId: d.mismatchId,
        severity: d.severity,
        message: this.translate.instant('mismatches.escalate.message', {
          type: this.translate.instant('mismatches.type.' + d.type),
          offerName: d.offer.offerName,
          skuCode: d.offer.skuCode,
          expected: d.expectedValue,
          actual: d.actualValue,
        }),
      }));
    },
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.escalate'));
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  protected onDragStart(event: PointerEvent): void {
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = this.detailPanel.width();

    const onMove = (e: PointerEvent) => {
      const delta = startX - e.clientX;
      this.detailPanel.resize(startWidth + delta);
    };

    const onUp = () => {
      document.removeEventListener('pointermove', onMove);
      document.removeEventListener('pointerup', onUp);
    };

    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
  }

  protected tabCls(id: DetailTab): Record<string, boolean> {
    const on = this.activeTab() === id;
    return {
      'cursor-pointer rounded-t-[var(--radius-md)] px-3 py-2 text-sm font-medium transition-colors': true,
      'bg-[var(--bg-tertiary)] text-[var(--text-primary)]': on,
      'text-[var(--text-secondary)]': !on,
    };
  }

  protected cellExpected(d: MismatchDetail): Record<string, boolean> {
    const diff = d.expectedValue !== d.actualValue;
    return {
      'text-[var(--text-primary)]': true,
      'bg-[color-mix(in_srgb,var(--status-warning)_12%,transparent)]': diff,
    };
  }

  protected cellActual(d: MismatchDetail): Record<string, boolean> {
    const diff = d.expectedValue !== d.actualValue;
    return {
      'text-[var(--text-primary)]': true,
      'bg-[color-mix(in_srgb,var(--status-error)_12%,transparent)]': diff,
    };
  }

  protected dot(eventType: string): string {
    if (eventType === 'DETECTED') return 'var(--status-error)';
    if (eventType.includes('RESOLVED')) return 'var(--status-success)';
    if (eventType.includes('ACKNOWLEDGED')) return 'var(--status-warning)';
    return 'var(--text-tertiary)';
  }

  protected canAck(): boolean {
    const d = this.detailQuery.data();
    return !!d && d.status === 'ACTIVE';
  }

  protected isTerminal(status: MismatchStatus): boolean {
    return TERMINAL_STATUSES.includes(status);
  }

  protected onResolve(payload: { resolution: string; note: string }): void {
    this.resolveMutation.mutate({ resolution: payload.resolution, note: payload.note });
  }

  protected onIgnoreConfirm(): void {
    this.showIgnoreModal.set(false);
    this.resolveMutation.mutate({
      resolution: 'IGNORED',
      note: this.translate.instant('mismatches.ignore.note'),
    });
  }

  protected onEscalate(): void {
    this.escalateMutation.mutate();
  }

  protected mpType(d: MismatchDetail): MarketplaceType {
    return mp(d.offer.marketplaceType);
  }

  protected badgeForStatus(st: MismatchStatus) {
    return stColor(st);
  }

  private invalidateAll(): void {
    this.queryClient.invalidateQueries({ queryKey: ['mismatch-detail'] });
    this.queryClient.invalidateQueries({ queryKey: ['mismatches'] });
    this.queryClient.invalidateQueries({ queryKey: ['mismatch-summary'] });
  }
}
