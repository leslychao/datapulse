import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, ExternalLink, RefreshCw } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { ConnectionSummary, CreateConnectionRequest, MarketplaceType } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { StatusLabelPipe, StatusColorPipe } from '@shared/pipes/status-label.pipe';

type FormStep = 'idle' | 'select-marketplace' | 'credentials';

@Component({
  selector: 'dp-connections-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    LucideAngularModule,
    TranslatePipe,
    StatusBadgeComponent,
    MarketplaceBadgeComponent,
    SectionCardComponent,
    SpinnerComponent,
    EmptyStateComponent,
    DateFormatPipe,
    StatusLabelPipe,
    StatusColorPipe,
  ],
  template: `
    <div class="max-w-4xl">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.connections.title' | translate }}</h1>
          <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.connections.subtitle' | translate }}</p>
        </div>
        <button
          (click)="startCreate()"
          class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          <lucide-icon [img]="PlusIcon" [size]="16" />
          {{ 'settings.connections.connect' | translate }}
        </button>
      </div>

      @if (formStep() !== 'idle') {
        <dp-section-card [title]="formStep() === 'select-marketplace'
          ? ('settings.connections.select_marketplace' | translate)
          : ('settings.connections.credentials_title' | translate)" class="mb-6">
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
              >{{ 'actions.cancel' | translate }}</button>
            </div>
          }

          @if (formStep() === 'credentials') {
            <form (ngSubmit)="submitCreate()" class="max-w-md space-y-4">
              <div class="flex items-center gap-2">
                <dp-marketplace-badge [type]="selectedMarketplace()!" />
                <span class="text-sm text-[var(--text-secondary)]">{{ selectedMarketplace() === 'WB' ? 'Wildberries' : 'Ozon' }}</span>
              </div>

              <div>
                <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connections.name_label' | translate }}</label>
                <input
                  type="text"
                  [(ngModel)]="formName"
                  name="name"
                  required
                  [placeholder]="'settings.connections.name_placeholder' | translate"
                  class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                />
              </div>

              @if (selectedMarketplace() === 'WB') {
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connections.wb_token_label' | translate }}</label>
                  <input
                    type="password"
                    [(ngModel)]="wbToken"
                    name="wbToken"
                    required
                    [placeholder]="'settings.connections.wb_token_placeholder' | translate"
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
              }

              @if (selectedMarketplace() === 'OZON') {
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connections.ozon_client_id_label' | translate }}</label>
                  <input
                    type="text"
                    [(ngModel)]="ozonClientId"
                    name="ozonClientId"
                    required
                    [placeholder]="'settings.connections.ozon_client_id_placeholder' | translate"
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.connections.ozon_api_key_label' | translate }}</label>
                  <input
                    type="password"
                    [(ngModel)]="ozonApiKey"
                    name="ozonApiKey"
                    required
                    [placeholder]="'settings.connections.ozon_api_key_placeholder' | translate"
                    class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  />
                </div>
              }

              @if (createMutation.error()) {
                <p class="text-sm text-[var(--status-error)]">{{ 'settings.connections.create_error_message' | translate }}</p>
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
                  {{ 'settings.connections.connect' | translate }}
                </button>
                <button
                  type="button"
                  (click)="cancelCreate()"
                  class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                >{{ 'actions.cancel' | translate }}</button>
              </div>
            </form>
          }
        </dp-section-card>
      }

      @if (connectionsQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (connectionsQuery.data(); as connections) {
        @if (connections.length === 0) {
          <dp-empty-state
            [message]="'settings.connections.empty' | translate"
            [hint]="'settings.connections.empty_hint' | translate"
          />
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.connections.col_marketplace' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.connections.col_name' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.connections.col_status' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.connections.col_last_sync' | translate }}</th>
                  <th class="px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (conn of connections; track conn.id) {
                  <tr
                    class="border-b border-[var(--border-subtle)] cursor-pointer transition-colors hover:bg-[var(--bg-secondary)]"
                    (click)="openDetail(conn)"
                  >
                    <td class="px-4 py-2.5">
                      <dp-marketplace-badge [type]="conn.marketplaceType" />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ conn.name }}</td>
                    <td class="px-4 py-2.5">
                      <dp-status-badge [label]="conn.status | dpStatusLabel" [color]="conn.status | dpStatusColor" />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                      {{ conn.lastSuccessAt | dpDateFormat }}
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
  private readonly translate = inject(TranslateService);

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
      this.toast.success(this.translate.instant('settings.connections.created'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.connections.create_error')),
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
}
