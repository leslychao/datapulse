import { ChangeDetectionStrategy, Component, effect, inject, input, NgZone, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { AgGridAngular } from 'ag-grid-angular';
import { ColDef, ICellRendererParams } from 'ag-grid-community';
import {
  LucideAngularModule,
  ArrowLeft,
  RefreshCw,
  Shield,
  Trash2,
  Power,
  PowerOff,
  Pencil,
} from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { translateApiErrorMessage } from '@core/i18n/translate-api-error';
import { CallLogEntry, SyncState } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { BreadcrumbService } from '@shared/services/breadcrumb.service';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { StatusLabelPipe, StatusColorPipe } from '@shared/pipes/status-label.pipe';
import { formatDateTime } from '@shared/utils/format.utils';
import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';

@Component({
  selector: 'dp-connection-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    AgGridAngular,
    LucideAngularModule,
    TranslatePipe,
    StatusBadgeComponent,
    MarketplaceBadgeComponent,
    SectionCardComponent,
    ConfirmationModalComponent,
    SpinnerComponent,
    DateFormatPipe,
    StatusLabelPipe,
    StatusColorPipe,
  ],
  template: `
    <div class="max-w-5xl">
      <button
        (click)="goBack()"
        class="mb-4 flex cursor-pointer items-center gap-1 text-sm text-[var(--text-secondary)] transition-colors hover:text-[var(--accent-primary)]"
      >
        <lucide-icon [img]="ArrowLeftIcon" [size]="14" />
        {{ 'settings.connection_detail.back' | translate }}
      </button>

      @if (connectionQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (connectionQuery.data(); as conn) {
        <div class="mb-6 flex items-center gap-3">
          <dp-marketplace-badge [type]="conn.marketplaceType" />
          @if (editingName()) {
            <form (ngSubmit)="submitNameEdit()" class="flex items-center gap-2">
              <input
                type="text"
                [(ngModel)]="editNameValue"
                name="editName"
                required
                class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-[var(--text-lg)] font-semibold text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
              <button
                type="submit"
                [disabled]="!editNameValue.trim() || editNameValue.trim() === conn.name || renameMutation.isPending()"
                class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              >{{ 'actions.save' | translate }}</button>
              <button
                type="button"
                (click)="editingName.set(false)"
                class="cursor-pointer rounded-[var(--radius-md)] px-3 py-1.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]"
              >{{ 'actions.cancel' | translate }}</button>
            </form>
          } @else {
            <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ conn.name }}</h1>
            @if (rbac.isAdmin()) {
              <button
                (click)="startNameEdit(conn.name)"
                class="cursor-pointer rounded p-1 text-[var(--text-tertiary)] hover:text-[var(--accent-primary)]"
              >
                <lucide-icon [img]="PencilIcon" [size]="14" />
              </button>
            }
          }
          <dp-status-badge [label]="conn.status | dpStatusLabel" [color]="conn.status | dpStatusColor" />
        </div>

        <dp-section-card [title]="'settings.connection_detail.info_title' | translate" class="mb-6">
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'settings.connection_detail.marketplace' | translate }}</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.marketplaceType === 'WB' ? 'Wildberries' : 'Ozon' }}</p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'settings.connection_detail.status' | translate }}</span>
              <p class="mt-0.5"><dp-status-badge [label]="conn.status | dpStatusLabel" [color]="conn.status | dpStatusColor" /></p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'settings.connection_detail.created_at' | translate }}</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.createdAt | dpDateFormat:'short' }}</p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'settings.connection_detail.last_success_sync' | translate }}</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.lastSuccessAt | dpDateFormat }}</p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'settings.connection_detail.last_error' | translate }}</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.lastErrorCode || '—' }}</p>
            </div>
          </div>
        </dp-section-card>

        @if (rbac.isAdmin()) {
          <dp-section-card [title]="'settings.connection_detail.credentials_title' | translate" class="mb-6">
            @if (!showRotation()) {
              <div class="flex items-center justify-between">
                <p class="text-sm text-[var(--text-secondary)]">{{ 'settings.connection_detail.credentials_hint' | translate }}</p>
                <button
                  (click)="showRotation.set(true)"
                  class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                >
                  <lucide-icon [img]="ShieldIcon" [size]="14" />
                  {{ 'settings.connection_detail.update_credentials' | translate }}
                </button>
              </div>
            } @else {
              <form (ngSubmit)="submitRotation()" class="max-w-md space-y-4">
                @if (conn.marketplaceType === 'WB') {
                  <div>
                    <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connection_detail.new_token_label' | translate }}</label>
                    <textarea
                      [(ngModel)]="newWbToken"
                      name="newWbToken"
                      required
                      rows="3"
                      class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                    ></textarea>
                  </div>
                } @else {
                  <div>
                    <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connection_detail.new_client_id_label' | translate }}</label>
                    <input
                      type="text"
                      [(ngModel)]="newOzonClientId"
                      name="newOzonClientId"
                      required
                      class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                    />
                  </div>
                  <div>
                    <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connection_detail.new_api_key_label' | translate }}</label>
                    <input
                      type="password"
                      [(ngModel)]="newOzonApiKey"
                      name="newOzonApiKey"
                      required
                      class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                    />
                  </div>
                }
                <div class="flex gap-3">
                  <button
                    type="submit"
                    [disabled]="!isRotationValid() || rotateMutation.isPending()"
                    class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
                  >{{ 'actions.save' | translate }}</button>
                  <button
                    type="button"
                    (click)="cancelRotation()"
                    class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]"
                  >{{ 'actions.cancel' | translate }}</button>
                </div>
              </form>
            }
          </dp-section-card>
        }

        <dp-section-card [title]="'settings.connection_detail.sync_domains_title' | translate" class="mb-6">
          <div class="mb-2 flex items-center justify-between">
            <p class="text-sm text-[var(--text-secondary)]">{{ 'settings.connection_detail.sync_domains_hint' | translate }}</p>
            @if (rbac.isAdmin()) {
              <button
                (click)="triggerSyncAll()"
                [disabled]="triggerSyncMutation.isPending() || conn.status !== 'ACTIVE'"
                class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                <lucide-icon [img]="RefreshIcon" [size]="14" [class.dp-spinner]="triggerSyncMutation.isPending()" />
                {{ 'settings.connection_detail.trigger_sync' | translate }}
              </button>
            }
          </div>
          @if (syncStateQuery.data(); as syncStates) {
            <ag-grid-angular
              class="ag-theme-alpine"
              [style.height.px]="Math.max(200, syncStates.length * 42 + 48)"
              style="width: 100%;"
              [rowData]="syncStates"
              [columnDefs]="syncColDefs"
              [defaultColDef]="defaultColDef"
              [animateRows]="true"
              [suppressCellFocus]="true"
              [localeText]="AG_GRID_LOCALE_RU"
            />
          }
        </dp-section-card>

        @if (rbac.isAdmin()) {
          <dp-section-card [title]="'settings.connection_detail.call_log_title' | translate" class="mb-6">
            @if (callLogQuery.data(); as callLogPage) {
              <ag-grid-angular
                class="ag-theme-alpine"
                style="width: 100%; height: 400px;"
                [rowData]="callLogPage.content"
                [columnDefs]="callLogColDefs"
                [defaultColDef]="defaultColDef"
                [animateRows]="true"
                [suppressCellFocus]="true"
                [pagination]="true"
                [paginationPageSize]="20"
                [localeText]="AG_GRID_LOCALE_RU"
              />
            }
          </dp-section-card>

          <dp-section-card [title]="'settings.connection_detail.danger_zone' | translate" class="mb-6">
            <div class="space-y-3">
              @if (conn.status !== 'DISABLED' && conn.status !== 'ARCHIVED') {
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-sm font-medium text-[var(--text-primary)]">{{ 'settings.connection_detail.disable_title' | translate }}</p>
                    <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">{{ 'settings.connection_detail.disable_hint' | translate }}</p>
                  </div>
                  <button
                    (click)="disableMutation.mutate(undefined)"
                    [disabled]="disableMutation.isPending()"
                    class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--status-warning)] px-3 py-1.5 text-sm text-[var(--status-warning)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-warning)_8%,transparent)]"
                  >
                    <lucide-icon [img]="PowerOffIcon" [size]="14" />
                    {{ 'settings.connection_detail.disable_btn' | translate }}
                  </button>
                </div>
              }
              @if (conn.status === 'DISABLED') {
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-sm font-medium text-[var(--text-primary)]">{{ 'settings.connection_detail.enable_title' | translate }}</p>
                    <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">{{ 'settings.connection_detail.enable_hint' | translate }}</p>
                  </div>
                  <button
                    (click)="enableMutation.mutate(undefined)"
                    [disabled]="enableMutation.isPending()"
                    class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--status-success)] px-3 py-1.5 text-sm text-[var(--status-success)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-success)_8%,transparent)]"
                  >
                    <lucide-icon [img]="PowerIcon" [size]="14" />
                    {{ 'settings.connection_detail.enable_btn' | translate }}
                  </button>
                </div>
              }
              @if (rbac.isOwner()) {
                <div class="flex items-center justify-between border-t border-[var(--border-default)] pt-3">
                  <div>
                    <p class="text-sm font-medium text-[var(--status-error)]">{{ 'settings.connection_detail.delete_title' | translate }}</p>
                    <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">{{ 'settings.connection_detail.delete_hint' | translate }}</p>
                  </div>
                  <button
                    (click)="showDeleteModal.set(true)"
                    class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--status-error)] px-3 py-1.5 text-sm text-[var(--status-error)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]"
                  >
                    <lucide-icon [img]="Trash2Icon" [size]="14" />
                    {{ 'actions.delete' | translate }}
                  </button>
                </div>
              }
            </div>
          </dp-section-card>
        }
      }

      @if (connectionQuery.data(); as conn) {
        <dp-confirmation-modal
          [open]="showDeleteModal()"
          [title]="'settings.connection_detail.delete_modal_title' | translate"
          [message]="translate.instant('settings.connection_detail.delete_modal_message', { name: conn.name })"
          [confirmLabel]="'actions.delete' | translate"
          [danger]="true"
          [typeToConfirm]="conn.name"
          (confirmed)="deleteMutation.mutate(undefined); showDeleteModal.set(false)"
          (cancelled)="showDeleteModal.set(false)"
        />
      }
    </div>
  `,
})
export class ConnectionDetailPageComponent {
  protected readonly ArrowLeftIcon = ArrowLeft;
  protected readonly RefreshIcon = RefreshCw;
  protected readonly ShieldIcon = Shield;
  protected readonly Trash2Icon = Trash2;
  protected readonly PowerIcon = Power;
  protected readonly PowerOffIcon = PowerOff;
  protected readonly PencilIcon = Pencil;
  protected readonly Math = Math;
  protected readonly AG_GRID_LOCALE_RU = AG_GRID_LOCALE_RU;

  readonly connectionId = input.required<string>();

  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly breadcrumbs = inject(BreadcrumbService);
  protected readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);
  private readonly zone = inject(NgZone);

  readonly editingName = signal(false);
  editNameValue = '';

  constructor() {
    effect(() => {
      const conn = this.connectionQuery.data();
      if (!conn) return;
      const wsId = this.wsStore.currentWorkspaceId();
      const base = `/workspace/${wsId}/settings`;
      this.breadcrumbs.setSegments([
        { label: this.translate.instant('settings.nav.title'), route: base },
        { label: this.translate.instant('settings.nav.connections'), route: `${base}/connections` },
        { label: conn.name, route: null },
      ]);
    });
  }

  readonly showRotation = signal(false);
  readonly showDeleteModal = signal(false);

  newWbToken = '';
  newOzonClientId = '';
  newOzonApiKey = '';

  private get connId(): number {
    return Number(this.connectionId());
  }

  readonly connectionQuery = injectQuery(() => ({
    queryKey: ['connection', this.connectionId()],
    queryFn: () => lastValueFrom(this.connectionApi.getConnection(this.connId)),
    staleTime: 30_000,
  }));

  readonly syncStateQuery = injectQuery(() => ({
    queryKey: ['connection-sync-state', this.connectionId()],
    queryFn: () => lastValueFrom(this.connectionApi.getSyncStates(this.connId)),
  }));

  readonly callLogQuery = injectQuery(() => ({
    queryKey: ['connection-call-log', this.connectionId()],
    queryFn: () => lastValueFrom(this.connectionApi.getCallLog(this.connId, {}, 0, 50)),
  }));

  readonly renameMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.connectionApi.updateConnectionName(this.connId, this.editNameValue.trim())),
    onSuccess: () => {
      this.editingName.set(false);
      this.connectionQuery.refetch();
      this.toast.success(this.translate.instant('settings.connection_detail.name_updated'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.connection_detail.name_update_error')),
  }));

  readonly rotateMutation = injectMutation(() => ({
    mutationFn: () => {
      const conn = this.connectionQuery.data()!;
      const credentials = conn.marketplaceType === 'WB'
        ? { apiToken: this.newWbToken.trim() }
        : { clientId: this.newOzonClientId.trim(), apiKey: this.newOzonApiKey.trim() };
      return lastValueFrom(this.connectionApi.updateCredentials(this.connId, { credentials }));
    },
    onSuccess: () => {
      this.connectionQuery.refetch();
      this.cancelRotation();
      this.toast.success(this.translate.instant('settings.connection_detail.credentials_updated'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.connection_detail.credentials_update_error')),
  }));

  readonly triggerSyncMutation = injectMutation(() => ({
    mutationFn: (domains?: string[]) =>
      lastValueFrom(this.connectionApi.triggerSync(this.connId, domains)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('settings.connection_detail.sync_started'));
      this.syncStateQuery.refetch();
    },
    onError: (error: unknown) =>
      this.toast.error(
        translateApiErrorMessage(
          this.translate,
          error,
          'settings.connection_detail.sync_start_error',
        ),
      ),
  }));

  readonly disableMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.disableConnection(this.connId)),
    onSuccess: () => {
      this.connectionQuery.refetch();
      this.toast.info(this.translate.instant('settings.connection_detail.disabled'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.connection_detail.disable_error')),
  }));

  readonly enableMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.enableConnection(this.connId)),
    onSuccess: () => {
      this.connectionQuery.refetch();
      this.toast.success(this.translate.instant('settings.connection_detail.enabled'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.connection_detail.enable_error')),
  }));

  readonly deleteMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.archiveConnection(this.connId)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('settings.connection_detail.deleted'));
      this.goBack();
    },
    onError: () => this.toast.error(this.translate.instant('settings.connection_detail.delete_error')),
  }));

  readonly defaultColDef: ColDef = {
    sortable: true,
    resizable: true,
    suppressMovable: true,
  };

  readonly syncColDefs: ColDef<SyncState>[] = [
    { headerName: this.translate.instant('settings.connection_detail.col_domain'), field: 'dataDomain', flex: 2 },
    {
      headerName: this.translate.instant('settings.connection_detail.col_status'),
      field: 'status',
      flex: 1,
      cellStyle: (params) => {
        if (params.value === 'IDLE') return { color: 'var(--status-success)' };
        if (params.value === 'SYNCING') return { color: 'var(--status-info)' };
        if (params.value === 'ERROR') return { color: 'var(--status-error)' };
        return { color: 'var(--text-secondary)' };
      },
    },
    {
      headerName: this.translate.instant('settings.connection_detail.col_last_sync'),
      field: 'lastSyncAt',
      flex: 2,
      valueFormatter: (params) => formatDateTime(params.value),
    },
    {
      headerName: this.translate.instant('settings.connection_detail.col_next_sync'),
      field: 'nextScheduledAt',
      flex: 2,
      valueFormatter: (params) => formatDateTime(params.value),
    },
    {
      headerName: this.translate.instant('settings.connection_detail.col_actions'),
      width: 140,
      cellRenderer: (params: ICellRendererParams<SyncState>) => {
        if (!this.rbac.isAdmin()) return '';
        const domain = params.data?.dataDomain;
        if (!domain) return '';
        const btn = document.createElement('button');
        btn.className = 'cursor-pointer text-xs text-[var(--accent-primary)] hover:underline';
        btn.textContent = this.translate.instant('settings.connection_detail.sync_domain_btn');
        btn.addEventListener('click', () => this.zone.run(() => this.triggerSyncDomain(domain)));
        return btn;
      },
      sortable: false,
      resizable: false,
    },
  ];

  readonly callLogColDefs: ColDef<CallLogEntry>[] = [
    {
      headerName: this.translate.instant('settings.connection_detail.col_time'),
      field: 'createdAt',
      flex: 2,
      valueFormatter: (params) => formatDateTime(params.value),
    },
    { headerName: this.translate.instant('settings.connection_detail.col_method'), field: 'httpMethod', width: 80 },
    {
      headerName: this.translate.instant('settings.connection_detail.col_endpoint'),
      field: 'endpoint',
      flex: 3,
    },
    {
      headerName: this.translate.instant('settings.connection_detail.col_http_status'),
      field: 'httpStatus',
      width: 80,
      cellStyle: (params) => {
        if (!params.value) return null;
        if (params.value >= 200 && params.value < 300) return { color: 'var(--status-success)' };
        if (params.value >= 400) return { color: 'var(--status-error)' };
        return null;
      },
    },
    { headerName: this.translate.instant('settings.connection_detail.col_duration'), field: 'durationMs', width: 100 },
    {
      headerName: this.translate.instant('settings.connection_detail.col_retry'),
      field: 'retryAttempt',
      width: 70,
    },
    {
      headerName: this.translate.instant('settings.connection_detail.col_error'),
      field: 'errorDetails',
      flex: 2,
      valueFormatter: (params) => params.value || '—',
    },
  ];

  startNameEdit(currentName: string): void {
    this.editNameValue = currentName;
    this.editingName.set(true);
  }

  submitNameEdit(): void {
    if (!this.editNameValue.trim()) return;
    this.renameMutation.mutate(undefined);
  }

  isRotationValid(): boolean {
    const conn = this.connectionQuery.data();
    if (!conn) return false;
    if (conn.marketplaceType === 'WB') return !!this.newWbToken.trim();
    return !!this.newOzonClientId.trim() && !!this.newOzonApiKey.trim();
  }

  cancelRotation(): void {
    this.showRotation.set(false);
    this.newWbToken = '';
    this.newOzonClientId = '';
    this.newOzonApiKey = '';
  }

  submitRotation(): void {
    if (!this.isRotationValid()) return;
    this.rotateMutation.mutate(undefined);
  }

  triggerSyncAll(): void {
    this.triggerSyncMutation.mutate(undefined);
  }

  triggerSyncDomain(domain: string): void {
    this.triggerSyncMutation.mutate([domain]);
  }

  goBack(): void {
    this.router.navigate(['/workspace', this.wsStore.currentWorkspaceId(), 'settings', 'connections']);
  }
}
