import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, ExternalLink, RefreshCw } from 'lucide-angular';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { ConnectionSummary, CreateConnectionRequest, MarketplaceType } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { SectionCardComponent } from '@shared/components/section-card.component';

type FormStep = 'idle' | 'select-marketplace' | 'credentials';

@Component({
  selector: 'dp-connections-page',
  standalone: true,
  imports: [
    FormsModule,
    LucideAngularModule,
    StatusBadgeComponent,
    MarketplaceBadgeComponent,
    SectionCardComponent,
  ],
  template: `
    <div class="max-w-4xl">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">Подключения</h1>
          <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">Управляйте подключениями к маркетплейсам</p>
        </div>
        <button
          (click)="startCreate()"
          class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          <lucide-icon [img]="PlusIcon" [size]="16" />
          Подключить
        </button>
      </div>

      <!-- Create form -->
      @if (formStep() !== 'idle') {
        <dp-section-card [title]="formStep() === 'select-marketplace' ? 'Выберите маркетплейс' : 'Данные подключения'" class="mb-6">
          @if (formStep() === 'select-marketplace') {
            <div class="flex gap-3">
              @for (mp of marketplaces; track mp.type) {
                <button
                  (click)="selectMarketplace(mp.type)"
                  class="flex cursor-pointer items-center gap-2 rounded-[var(--radius-md)] border border-[var(--border-default)] px-5 py-3 text-sm font-medium text-[var(--text-primary)] transition-colors hover:border-[var(--accent-primary)] hover:bg-[var(--accent-subtle)]"
                >
                  <dp-marketplace-badge [type]="mp.type" />
                  {{ mp.label }}
                </button>
              }
              <button
                (click)="cancelCreate()"
                class="ml-auto cursor-pointer px-3 py-2 text-sm text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              >Отмена</button>
            </div>
          }

          @if (formStep() === 'credentials') {
            <form (ngSubmit)="submitCreate()" class="max-w-md space-y-4">
              <div class="flex items-center gap-2">
                <dp-marketplace-badge [type]="selectedMarketplace()!" />
                <span class="text-sm text-[var(--text-secondary)]">{{ selectedMarketplace() === 'WB' ? 'Wildberries' : 'Ozon' }}</span>
              </div>

              <div>
                <label class="mb-1 block text-sm text-[var(--text-secondary)]">Название подключения</label>
                <input
                  type="text"
                  [(ngModel)]="formName"
                  name="name"
                  required
                  placeholder="Мой магазин"
                  class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                />
              </div>

              @if (selectedMarketplace() === 'WB') {
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">API-токен</label>
                  <input
                    type="password"
                    [(ngModel)]="wbToken"
                    name="wbToken"
                    required
                    placeholder="Вставьте токен из личного кабинета WB"
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
              }

              @if (selectedMarketplace() === 'OZON') {
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">Client ID</label>
                  <input
                    type="text"
                    [(ngModel)]="ozonClientId"
                    name="ozonClientId"
                    required
                    placeholder="Числовой Client ID"
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">API Key</label>
                  <input
                    type="password"
                    [(ngModel)]="ozonApiKey"
                    name="ozonApiKey"
                    required
                    placeholder="API Key из личного кабинета Ozon"
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
              }

              @if (createMutation.error()) {
                <p class="text-sm text-[var(--status-error)]">Ошибка: не удалось создать подключение. Проверьте данные.</p>
              }

              <div class="flex gap-3 pt-2">
                <button
                  type="submit"
                  [disabled]="!isFormValid() || createMutation.isPending()"
                  class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  @if (createMutation.isPending()) {
                    <span class="dp-spinner mr-1 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white"></span>
                  }
                  Подключить
                </button>
                <button
                  type="button"
                  (click)="cancelCreate()"
                  class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                >Отмена</button>
              </div>
            </form>
          }
        </dp-section-card>
      }

      <!-- Connection list -->
      @if (connectionsQuery.isPending()) {
        <div class="flex items-center gap-2 py-8 text-sm text-[var(--text-secondary)]">
          <span class="dp-spinner inline-block h-4 w-4 rounded-full border-2 border-[var(--border-default)] border-t-[var(--accent-primary)]"></span>
          Загрузка...
        </div>
      }

      @if (connectionsQuery.data(); as connections) {
        @if (connections.length === 0) {
          <div class="rounded-[var(--radius-md)] border border-dashed border-[var(--border-default)] bg-[var(--bg-secondary)] py-12 text-center">
            <p class="text-sm text-[var(--text-secondary)]">Нет подключений</p>
            <p class="mt-1 text-[var(--text-xs)] text-[var(--text-tertiary)]">Нажмите «Подключить», чтобы добавить первый маркетплейс</p>
          </div>
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Маркетплейс</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Название</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Статус</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Последняя синхронизация</th>
                  <th class="px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (conn of connections; track conn.id) {
                  <tr
                    class="border-b border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-secondary)] cursor-pointer"
                    (click)="openDetail(conn)"
                  >
                    <td class="px-4 py-2.5">
                      <dp-marketplace-badge [type]="$any(conn.marketplaceType)" />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ conn.name }}</td>
                    <td class="px-4 py-2.5">
                      <dp-status-badge [label]="statusLabel(conn.status)" [color]="statusColor(conn.status)" />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                      {{ conn.lastSuccessAt ? formatDate(conn.lastSuccessAt) : '—' }}
                    </td>
                    <td class="px-4 py-2.5 text-right">
                      <lucide-icon [img]="ExternalLinkIcon" [size]="14" class="text-[var(--text-tertiary)]" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </div>
  `,
})
export class ConnectionsPageComponent {
  protected readonly PlusIcon = Plus;
  protected readonly ExternalLinkIcon = ExternalLink;
  protected readonly RefreshIcon = RefreshCw;

  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly formStep = signal<FormStep>('idle');
  readonly selectedMarketplace = signal<MarketplaceType | null>(null);

  formName = '';
  wbToken = '';
  ozonClientId = '';
  ozonApiKey = '';

  readonly marketplaces = [
    { type: 'WB' as MarketplaceType, label: 'Wildberries' },
    { type: 'OZON' as MarketplaceType, label: 'Ozon' },
  ];

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
  }));

  readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateConnectionRequest) => lastValueFrom(this.connectionApi.createConnection(req)),
    onSuccess: () => {
      this.connectionsQuery.refetch();
      this.cancelCreate();
      this.toast.success('Подключение создано. Проверка учётных данных запущена.');
    },
  }));

  startCreate(): void {
    this.formStep.set('select-marketplace');
  }

  selectMarketplace(type: MarketplaceType): void {
    this.selectedMarketplace.set(type);
    this.formStep.set('credentials');
  }

  cancelCreate(): void {
    this.formStep.set('idle');
    this.selectedMarketplace.set(null);
    this.formName = '';
    this.wbToken = '';
    this.ozonClientId = '';
    this.ozonApiKey = '';
  }

  isFormValid(): boolean {
    if (!this.formName.trim()) return false;
    if (this.selectedMarketplace() === 'WB') return !!this.wbToken.trim();
    return !!this.ozonClientId.trim() && !!this.ozonApiKey.trim();
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const mp = this.selectedMarketplace()!;
    const credentials = mp === 'WB'
      ? { apiToken: this.wbToken.trim() }
      : { clientId: this.ozonClientId.trim(), apiKey: this.ozonApiKey.trim() };

    this.createMutation.mutate({
      marketplaceType: mp,
      name: this.formName.trim(),
      credentials,
    });
  }

  openDetail(conn: ConnectionSummary): void {
    this.router.navigate([
      '/workspace', this.wsStore.currentWorkspaceId(), 'settings', 'connections', conn.id,
    ]);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'Активно';
      case 'PENDING_VALIDATION': return 'Проверка';
      case 'AUTH_FAILED': return 'Ошибка авторизации';
      case 'DISABLED': return 'Отключено';
      case 'ARCHIVED': return 'В архиве';
      default: return status;
    }
  }

  statusColor(status: string): 'success' | 'error' | 'warning' | 'info' | 'neutral' {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'PENDING_VALIDATION': return 'info';
      case 'AUTH_FAILED': return 'error';
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
