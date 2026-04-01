import { Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { AgGridAngular } from 'ag-grid-angular';
import { ColDef, GridReadyEvent, GridApi } from 'ag-grid-community';
import {
  LucideAngularModule,
  ArrowLeft,
  RefreshCw,
  Shield,
  Trash2,
  Power,
  PowerOff,
} from 'lucide-angular';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { CallLogEntry, MarketplaceType, SyncState } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-connection-detail-page',
  standalone: true,
  imports: [
    FormsModule,
    AgGridAngular,
    LucideAngularModule,
    StatusBadgeComponent,
    MarketplaceBadgeComponent,
    SectionCardComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="max-w-5xl">
      <!-- Back button -->
      <button
        (click)="goBack()"
        class="mb-4 flex cursor-pointer items-center gap-1 text-sm text-[var(--text-secondary)] transition-colors hover:text-[var(--accent-primary)]"
      >
        <lucide-icon [img]="ArrowLeftIcon" [size]="14" />
        Назад к подключениям
      </button>

      @if (connectionQuery.isPending()) {
        <div class="flex items-center gap-2 py-8 text-sm text-[var(--text-secondary)]">
          <span class="dp-spinner inline-block h-4 w-4 rounded-full border-2 border-[var(--border-default)] border-t-[var(--accent-primary)]"></span>
          Загрузка...
        </div>
      }

      @if (connectionQuery.data(); as conn) {
        <!-- Header -->
        <div class="mb-6 flex items-center gap-3">
          <dp-marketplace-badge [type]="$any(conn.marketplaceType)" />
          <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ conn.name }}</h1>
          <dp-status-badge [label]="statusLabel(conn.status)" [color]="statusColor(conn.status)" />
        </div>

        <!-- Info section -->
        <dp-section-card title="Информация" class="mb-6">
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span class="text-[var(--text-secondary)]">Маркетплейс</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.marketplaceType === 'WB' ? 'Wildberries' : 'Ozon' }}</p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">Статус</span>
              <p class="mt-0.5"><dp-status-badge [label]="statusLabel(conn.status)" [color]="statusColor(conn.status)" /></p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">Последняя успешная синхронизация</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.lastSuccessAt ? formatDate(conn.lastSuccessAt) : '—' }}</p>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">Последняя ошибка</span>
              <p class="mt-0.5 text-[var(--text-primary)]">{{ conn.lastErrorCode || '—' }}</p>
            </div>
          </div>
        </dp-section-card>

        <!-- Credential rotation -->
        <dp-section-card title="Учётные данные" class="mb-6">
          @if (!showRotation()) {
            <div class="flex items-center justify-between">
              <p class="text-sm text-[var(--text-secondary)]">Обновите учётные данные, если токен/ключ был перевыпущен в личном кабинете маркетплейса.</p>
              <button
                (click)="showRotation.set(true)"
                class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                <lucide-icon [img]="ShieldIcon" [size]="14" />
                Обновить
              </button>
            </div>
          } @else {
            <form (ngSubmit)="submitRotation()" class="max-w-md space-y-4">
              @if (conn.marketplaceType === 'WB') {
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">Новый API-токен</label>
                  <input
                    type="password"
                    [(ngModel)]="newWbToken"
                    name="newWbToken"
                    required
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
              } @else {
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">Новый Client ID</label>
                  <input
                    type="text"
                    [(ngModel)]="newOzonClientId"
                    name="newOzonClientId"
                    required
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">Новый API Key</label>
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
                >Сохранить</button>
                <button
                  type="button"
                  (click)="cancelRotation()"
                  class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]"
                >Отмена</button>
              </div>
            </form>
          }
        </dp-section-card>

        <!-- Sync State (AG Grid) -->
        <dp-section-card title="Синхронизация по доменам" class="mb-6">
          <div class="mb-2 flex items-center justify-between">
            <p class="text-sm text-[var(--text-secondary)]">Текущее состояние синхронизации по типам данных</p>
            <button
              (click)="triggerSyncMutation.mutate(undefined)"
              [disabled]="triggerSyncMutation.isPending() || conn.status !== 'ACTIVE'"
              class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              <lucide-icon [img]="RefreshIcon" [size]="14" [class.dp-spinner]="triggerSyncMutation.isPending()" />
              Запустить синхронизацию
            </button>
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
            />
          }
        </dp-section-card>

        <!-- Call Log (AG Grid) -->
        <dp-section-card title="Журнал API-вызовов" class="mb-6">
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
            />
          }
        </dp-section-card>

        <!-- Danger zone -->
        <dp-section-card title="Опасная зона" class="mb-6">
          <div class="space-y-3">
            @if (conn.status !== 'DISABLED' && conn.status !== 'ARCHIVED') {
              <div class="flex items-center justify-between">
                <div>
                  <p class="text-sm font-medium text-[var(--text-primary)]">Отключить подключение</p>
                  <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">Синхронизация данных будет приостановлена</p>
                </div>
                <button
                  (click)="disableMutation.mutate(undefined)"
                  [disabled]="disableMutation.isPending()"
                  class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--status-warning)] px-3 py-1.5 text-sm text-[var(--status-warning)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-warning)_8%,transparent)]"
                >
                  <lucide-icon [img]="PowerOffIcon" [size]="14" />
                  Отключить
                </button>
              </div>
            }
            @if (conn.status === 'DISABLED') {
              <div class="flex items-center justify-between">
                <div>
                  <p class="text-sm font-medium text-[var(--text-primary)]">Включить подключение</p>
                  <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">Синхронизация будет возобновлена после ревалидации</p>
                </div>
                <button
                  (click)="enableMutation.mutate(undefined)"
                  [disabled]="enableMutation.isPending()"
                  class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--status-success)] px-3 py-1.5 text-sm text-[var(--status-success)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-success)_8%,transparent)]"
                >
                  <lucide-icon [img]="PowerIcon" [size]="14" />
                  Включить
                </button>
              </div>
            }
            <div class="flex items-center justify-between border-t border-[var(--border-default)] pt-3">
              <div>
                <p class="text-sm font-medium text-[var(--status-error)]">Удалить подключение</p>
                <p class="text-[var(--text-xs)] text-[var(--text-secondary)]">Это действие необратимо. Все данные синхронизации будут потеряны.</p>
              </div>
              <button
                (click)="showDeleteModal.set(true)"
                class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--status-error)] px-3 py-1.5 text-sm text-[var(--status-error)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]"
              >
                <lucide-icon [img]="Trash2Icon" [size]="14" />
                Удалить
              </button>
            </div>
          </div>
        </dp-section-card>
      }

      @if (connectionQuery.data(); as conn) {
        <dp-confirmation-modal
          [open]="showDeleteModal()"
          title="Удалить подключение"
          [message]="'Вы уверены, что хотите удалить подключение «' + conn.name + '»? Это действие необратимо.'"
          confirmLabel="Удалить"
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
  protected readonly Math = Math;

  readonly connectionId = input.required<string>();

  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

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
  }));

  readonly syncStateQuery = injectQuery(() => ({
    queryKey: ['connection-sync-state', this.connectionId()],
    queryFn: () => lastValueFrom(this.connectionApi.getSyncStates(this.connId)),
  }));

  readonly callLogQuery = injectQuery(() => ({
    queryKey: ['connection-call-log', this.connectionId()],
    queryFn: () => lastValueFrom(this.connectionApi.getCallLog(this.connId, {}, 0, 50)),
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
      this.toast.success('Учётные данные обновлены. Запущена проверка.');
    },
    onError: () => this.toast.error('Не удалось обновить учётные данные'),
  }));

  readonly triggerSyncMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.triggerSync(this.connId)),
    onSuccess: () => {
      this.toast.success('Синхронизация запущена');
      this.syncStateQuery.refetch();
    },
    onError: () => this.toast.error('Не удалось запустить синхронизацию'),
  }));

  readonly disableMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.disableConnection(this.connId)),
    onSuccess: () => {
      this.connectionQuery.refetch();
      this.toast.info('Подключение отключено');
    },
  }));

  readonly enableMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.enableConnection(this.connId)),
    onSuccess: () => {
      this.connectionQuery.refetch();
      this.toast.success('Подключение активировано. Запущена проверка.');
    },
  }));

  readonly deleteMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.connectionApi.archiveConnection(this.connId)),
    onSuccess: () => {
      this.toast.success('Подключение удалено');
      this.goBack();
    },
    onError: () => this.toast.error('Не удалось удалить подключение'),
  }));

  readonly defaultColDef: ColDef = {
    sortable: true,
    resizable: true,
    suppressMovable: true,
  };

  readonly syncColDefs: ColDef<SyncState>[] = [
    { headerName: 'Домен', field: 'dataDomain', flex: 2 },
    {
      headerName: 'Статус',
      field: 'status',
      flex: 1,
      cellStyle: (params) => {
        if (params.value === 'OK') return { color: 'var(--status-success)' };
        if (params.value === 'ERROR') return { color: 'var(--status-error)' };
        return { color: 'var(--status-warning)' };
      },
    },
    {
      headerName: 'Последняя синхронизация',
      field: 'lastSyncAt',
      flex: 2,
      valueFormatter: (params) => params.value ? this.formatDate(params.value) : '—',
    },
    {
      headerName: 'Следующая',
      field: 'nextScheduledAt',
      flex: 2,
      valueFormatter: (params) => params.value ? this.formatDate(params.value) : '—',
    },
  ];

  readonly callLogColDefs: ColDef<CallLogEntry>[] = [
    {
      headerName: 'Время',
      field: 'createdAt',
      flex: 2,
      valueFormatter: (params) => this.formatDate(params.value),
    },
    { headerName: 'Метод', field: 'httpMethod', width: 80 },
    { headerName: 'Endpoint', field: 'endpoint', flex: 3 },
    {
      headerName: 'Статус',
      field: 'httpStatus',
      width: 80,
      cellStyle: (params) => {
        if (!params.value) return null;
        if (params.value >= 200 && params.value < 300) return { color: 'var(--status-success)' };
        if (params.value >= 400) return { color: 'var(--status-error)' };
        return null;
      },
    },
    { headerName: 'Время (мс)', field: 'durationMs', width: 100 },
    { headerName: 'Retry', field: 'retryAttempt', width: 70 },
    {
      headerName: 'Ошибка',
      field: 'errorDetails',
      flex: 2,
      valueFormatter: (params) => params.value || '—',
    },
  ];

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

  goBack(): void {
    this.router.navigate(['/workspace', this.wsStore.currentWorkspaceId(), 'settings', 'connections']);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'Активно';
      case 'PENDING_VALIDATION': return 'Проверка';
      case 'AUTH_FAILED': return 'Ошибка авторизации';
      case 'ERROR': return 'Ошибка';
      case 'DISABLED': return 'Отключено';
      case 'ARCHIVED': return 'В архиве';
      default: return status;
    }
  }

  statusColor(status: string): 'success' | 'error' | 'warning' | 'info' | 'neutral' {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'PENDING_VALIDATION': return 'info';
      case 'AUTH_FAILED': case 'ERROR': return 'error';
      case 'DISABLED': case 'ARCHIVED': return 'neutral';
      default: return 'neutral';
    }
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }
}
