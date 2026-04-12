import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectMutation,
  injectQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  ArrowLeft,
  Check,
  Ban,
  ArrowUpRight,
  CircleCheck,
  RefreshCw,
} from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { AlertApiService } from '@core/api/alert-api.service';
import { MismatchApiService } from '@core/api/mismatch-api.service';
import { MarketplaceType, MismatchDetail, MismatchStatus } from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { MismatchResolveModalComponent } from './mismatch-resolve-modal.component';

const TERMINAL_STATUSES: MismatchStatus[] = ['RESOLVED', 'AUTO_RESOLVED', 'IGNORED'];

function mpType(t: string): MarketplaceType {
  return t as MarketplaceType;
}

function statusColor(st: MismatchStatus): StatusColor {
  if (st === 'ACTIVE') return 'error';
  if (st === 'RESOLVED' || st === 'AUTO_RESOLVED') return 'success';
  if (st === 'ACKNOWLEDGED') return 'warning';
  return 'neutral';
}

const TYPE_COLOR: Record<string, StatusColor> = {
  PRICE: 'info',
  STOCK: 'warning',
  PROMO: 'info',
  FINANCE: 'info',
};

@Component({
  selector: 'dp-mismatch-detail-page',
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
  templateUrl: './mismatch-detail-page.component.html',
})
export class MismatchDetailPageComponent {
  readonly mismatchId = input.required<string>();

  private readonly api = inject(MismatchApiService);
  private readonly actionApi = inject(ActionApiService);
  private readonly alertApi = inject(AlertApiService);
  protected readonly ws = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly BackIcon = ArrowLeft;
  readonly CheckIcon = Check;
  readonly BanIcon = Ban;
  readonly EscalateIcon = ArrowUpRight;
  readonly ResolveIcon = CircleCheck;
  readonly RefreshIcon = RefreshCw;

  readonly showResolveModal = signal(false);
  readonly showIgnoreModal = signal(false);

  private readonly numericId = computed(() => {
    const n = Number(this.mismatchId());
    return Number.isFinite(n) ? n : 0;
  });

  readonly detailQuery = injectQuery(() => ({
    queryKey: ['mismatch-detail', this.ws.currentWorkspaceId(), this.numericId()],
    queryFn: () =>
      lastValueFrom(this.api.getDetail(this.ws.currentWorkspaceId()!, this.numericId())),
    enabled: !!this.ws.currentWorkspaceId() && this.numericId() > 0,
  }));

  readonly d = computed(() => this.detailQuery.data() ?? null);

  readonly isTerminal = computed(() => {
    const data = this.d();
    return data ? TERMINAL_STATUSES.includes(data.status) : false;
  });

  readonly isActive = computed(() => this.d()?.status === 'ACTIVE');
  readonly isAcknowledged = computed(() => this.d()?.status === 'ACKNOWLEDGED');


  protected mpType(d: MismatchDetail): MarketplaceType {
    return mpType(d.offer.marketplaceType);
  }

  protected statusColor(st: MismatchStatus): StatusColor {
    return statusColor(st);
  }

  protected typeColor(type: string): StatusColor {
    return TYPE_COLOR[type] ?? 'info';
  }

  protected cellExpected(d: MismatchDetail): Record<string, boolean> {
    return {
      'text-[var(--text-primary)]': true,
      'bg-[color-mix(in_srgb,var(--status-warning)_12%,transparent)]': d.expectedValue !== d.actualValue,
    };
  }

  protected cellActual(d: MismatchDetail): Record<string, boolean> {
    return {
      'text-[var(--text-primary)]': true,
      'bg-[color-mix(in_srgb,var(--status-error)_12%,transparent)]': d.expectedValue !== d.actualValue,
    };
  }

  protected dot(eventType: string): string {
    if (eventType === 'DETECTED') return 'var(--status-error)';
    if (eventType.includes('RESOLVED')) return 'var(--status-success)';
    if (eventType.includes('ACKNOWLEDGED')) return 'var(--status-warning)';
    return 'var(--text-tertiary)';
  }

  goBack(): void {
    void this.router.navigate(['..'], {
      relativeTo: (this.router as any).routerState?.root,
    });
    void this.router.navigate(
      ['/workspace', this.ws.currentWorkspaceId(), 'mismatches'],
    );
  }

  // --- Mutations ---

  readonly ackMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.api.acknowledge(this.ws.currentWorkspaceId()!, this.numericId())),
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.ack'));
      this.invalidateAll();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly retryMutation = injectMutation(() => ({
    mutationFn: () => {
      const id = this.d()?.relatedActionId;
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
      lastValueFrom(this.api.resolve(this.ws.currentWorkspaceId()!, this.numericId(), body)),
    onSuccess: () => {
      this.showResolveModal.set(false);
      this.toast.success(this.translate.instant('mismatches.toast.resolve'));
      this.invalidateAll();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly escalateMutation = injectMutation(() => ({
    mutationFn: () => {
      const data = this.d();
      if (!data) throw new Error('no data');
      return lastValueFrom(this.alertApi.createAlert({
        sourceType: 'MISMATCH',
        sourceId: data.mismatchId,
        severity: data.severity,
        message: this.translate.instant('mismatches.escalate.message', {
          type: this.translate.instant('mismatches.type.' + data.type),
          offerName: data.offer.offerName,
          skuCode: data.offer.skuCode,
          expected: data.expectedValue,
          actual: data.actualValue,
        }),
      }));
    },
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.escalate'));
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  onResolve(payload: { resolution: string; note: string }): void {
    this.resolveMutation.mutate(payload);
  }

  onIgnoreConfirm(): void {
    this.showIgnoreModal.set(false);
    this.resolveMutation.mutate({
      resolution: 'IGNORED',
      note: this.translate.instant('mismatches.ignore.note'),
    });
  }

  onEscalate(): void {
    this.escalateMutation.mutate();
  }

  private invalidateAll(): void {
    this.queryClient.invalidateQueries({ queryKey: ['mismatch-detail'] });
    this.queryClient.invalidateQueries({ queryKey: ['mismatches'] });
    this.queryClient.invalidateQueries({ queryKey: ['mismatch-summary'] });
  }
}
