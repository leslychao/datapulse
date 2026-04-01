import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import {
  CreateLockRequest,
  ManualPriceLock,
  PricingLockFilter,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-locks-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div
        class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3"
      >
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'pricing.locks.title' | translate }}
        </h2>
        <button
          (click)="showCreateForm.set(true)"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          {{ 'pricing.locks.create' | translate }}
        </button>
      </div>

      <!-- Create Form Panel -->
      @if (showCreateForm()) {
        <div
          class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-4"
        >
          <div class="flex flex-wrap items-end gap-4">
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.locks.form.offer_id_label' | translate }}
              </label>
              <input
                type="number"
                [(ngModel)]="formOfferId"
                class="h-8 w-36 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                placeholder="12345"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.locks.form.price_label' | translate }}
              </label>
              <input
                type="number"
                [(ngModel)]="formPrice"
                class="h-8 w-32 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                placeholder="1000"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.locks.form.reason_label' | translate }}
              </label>
              <input
                type="text"
                [(ngModel)]="formReason"
                class="h-8 w-56 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                [placeholder]="'pricing.locks.form.optional' | translate"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.locks.form.expires_at' | translate }}
              </label>
              <input
                type="date"
                [(ngModel)]="formExpiresAt"
                class="h-8 w-40 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
            <div class="flex gap-2">
              <button
                (click)="submitCreate()"
                [disabled]="!isFormValid()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ 'pricing.locks.form.submit' | translate }}
              </button>
              <button
                (click)="cancelCreate()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                {{ 'actions.cancel' | translate }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Filter Bar -->
      <dp-filter-bar
        [filters]="filterConfigs"
        [values]="filterValues()"
        (filtersChanged)="onFiltersChanged($event)"
      />

      <!-- Data Grid -->
      <div class="flex-1 px-4 py-3">
        @if (locksQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.locks.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="locksQuery.refetch()"
          />
        } @else if (!locksQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="'pricing.locks.empty' | translate"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="locksQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
          />
        }
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showUnlockModal()"
      [title]="'pricing.locks.unlock_title' | translate"
      [message]="unlockMessage()"
      [confirmLabel]="'pricing.locks.unlock_confirm' | translate"
      (confirmed)="executeUnlock()"
      (cancelled)="showUnlockModal.set(false)"
    />
  `,
})
export class LocksPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);
  readonly currentSort = signal('lockedAt,desc');

  readonly showCreateForm = signal(false);
  readonly showUnlockModal = signal(false);
  readonly unlockTarget = signal<ManualPriceLock | null>(null);

  formOfferId: number | null = null;
  formPrice: number | null = null;
  formReason = '';
  formExpiresAt = '';

  readonly filterConfigs: FilterConfig[] = [
    { key: 'search', label: this.translate.instant('pricing.locks.filter.search'), type: 'text' },
  ];

  readonly columnDefs = [
    {
      headerName: this.translate.instant('pricing.locks.col.offer'),
      field: 'offerName',
      minWidth: 250,
      flex: 1,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.locks.col.sku'),
      field: 'sellerSku',
      width: 120,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: this.translate.instant('pricing.locks.col.connection'),
      field: 'connectionName',
      width: 160,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.locks.col.price'),
      field: 'lockedPrice',
      width: 120,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: this.translate.instant('pricing.locks.col.reason'),
      field: 'reason',
      minWidth: 200,
      flex: 1,
      sortable: false,
      valueFormatter: (params: any) => params.value ?? '—',
    },
    {
      headerName: this.translate.instant('pricing.locks.col.locked_by'),
      field: 'lockedByName',
      width: 140,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.locks.col.locked_at'),
      field: 'lockedAt',
      width: 140,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => this.formatTimestamp(params.value),
    },
    {
      headerName: this.translate.instant('pricing.locks.col.expires_at'),
      field: 'expiresAt',
      width: 140,
      sortable: true,
      valueFormatter: (params: any) =>
        params.value
          ? this.formatTimestamp(params.value)
          : this.translate.instant('pricing.locks.indefinite'),
    },
    {
      headerName: this.translate.instant('pricing.locks.col.remaining'),
      field: 'expiresAt',
      colId: 'timeRemaining',
      width: 120,
      sortable: false,
      cellRenderer: (params: any) => {
        if (!params.data?.expiresAt) {
          return `<span class="text-[var(--text-secondary)]">${this.translate.instant('pricing.locks.indefinite')}</span>`;
        }
        const remaining =
          new Date(params.data.expiresAt).getTime() - Date.now();
        if (remaining <= 0) {
          return `<span style="color: var(--status-error)">${this.translate.instant('pricing.locks.expired')}</span>`;
        }
        const hours = Math.floor(remaining / 3_600_000);
        const days = Math.floor(hours / 24);
        const remHours = hours % 24;
        const color =
          hours < 24 ? 'color: var(--status-error)' : '';
        const dUnit = this.translate.instant('common.time.day_short');
        const hUnit = this.translate.instant('common.time.hour_short');
        if (days > 0) {
          return `<span style="${color}">${days} ${dUnit} ${remHours} ${hUnit}</span>`;
        }
        return `<span style="${color}">${hours} ${hUnit}</span>`;
      },
    },
    {
      headerName: '',
      field: 'actions',
      width: 60,
      sortable: false,
      suppressMovable: true,
      cellRenderer: () =>
        `<button class="action-btn" data-action="unlock" title="${this.translate.instant('actions.unlock')}">🔓</button>`,
      onCellClicked: (params: any) => {
        const target = params.event?.target as HTMLElement;
        const action = target
          ?.closest('[data-action]')
          ?.getAttribute('data-action');
        if (action === 'unlock' && params.data) {
          this.unlockTarget.set(params.data);
          this.showUnlockModal.set(true);
        }
      },
    },
  ];

  private readonly filter = computed<PricingLockFilter>(() => {
    const vals = this.filterValues();
    const f: PricingLockFilter = {};
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly locksQuery = injectQuery(() => ({
    queryKey: [
      'locks',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.currentPage(),
      this.currentSort(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listLocks(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
          this.currentSort(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.locksQuery.data()?.content ?? []);

  readonly unlockMessage = computed(() => {
    const lock = this.unlockTarget();
    return lock
      ? this.translate.instant('pricing.locks.unlock_message', {
          offer: lock.offerName, sku: lock.sellerSku,
        })
      : '';
  });

  private readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateLockRequest) =>
      lastValueFrom(
        this.pricingApi.createLock(this.wsStore.currentWorkspaceId()!, req),
      ),
    onSuccess: () => {
      this.resetForm();
      this.queryClient.invalidateQueries({ queryKey: ['locks'] });
      this.toast.success(this.translate.instant('pricing.locks.created'));
    },
    onError: () => this.toast.error(this.translate.instant('pricing.locks.create_error')),
  }));

  private readonly deleteMutation = injectMutation(() => ({
    mutationFn: (lockId: number) =>
      lastValueFrom(
        this.pricingApi.deleteLock(
          this.wsStore.currentWorkspaceId()!,
          lockId,
        ),
      ),
    onSuccess: () => {
      this.showUnlockModal.set(false);
      this.unlockTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['locks'] });
      this.toast.success(this.translate.instant('pricing.locks.unlocked'));
    },
    onError: () => {
      this.showUnlockModal.set(false);
      this.toast.error(this.translate.instant('pricing.locks.unlock_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  isFormValid(): boolean {
    return this.formOfferId !== null && this.formPrice !== null;
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const req: CreateLockRequest = {
      marketplaceOfferId: this.formOfferId!,
      lockedPrice: this.formPrice!,
    };
    if (this.formReason.trim()) {
      req.reason = this.formReason.trim();
    }
    if (this.formExpiresAt) {
      req.expiresAt = this.formExpiresAt;
    }
    this.createMutation.mutate(req);
  }

  cancelCreate(): void {
    this.resetForm();
  }

  executeUnlock(): void {
    const target = this.unlockTarget();
    if (target) {
      this.deleteMutation.mutate(target.id);
    }
  }

  private resetForm(): void {
    this.showCreateForm.set(false);
    this.formOfferId = null;
    this.formPrice = null;
    this.formReason = '';
    this.formExpiresAt = '';
  }

  private formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  private formatTimestamp(iso: string | null): string {
    return formatDateTime(iso, 'full');
  }
}
