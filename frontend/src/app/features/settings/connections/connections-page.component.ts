import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, ExternalLink, RefreshCw } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { RbacService } from '@core/auth/rbac.service';
import {
  ConnectionSummary,
  CreateConnectionRequest,
  MarketplaceType,
  MARKETPLACE_REGISTRY,
  MarketplaceConfig,
  getMarketplaceConfig,
} from '@core/models';
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
        @if (rbac.isAdmin()) {
          <button
            (click)="startCreate()"
            class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            <lucide-icon [img]="PlusIcon" [size]="16" />
            {{ 'settings.connections.connect' | translate }}
          </button>
        }
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
                <span class="text-sm text-[var(--text-secondary)]">{{ selectedConfig()?.label }}</span>
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

              @if (selectedConfig(); as config) {
                @for (field of config.credentialFields; track field.key) {
                  <div>
                    <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ field.labelKey | translate }}</label>
                    @if (field.inputType === 'textarea') {
                      <textarea
                        [(ngModel)]="credentialValues[field.key]"
                        [name]="field.key"
                        required
                        rows="3"
                        [placeholder]="field.placeholderKey ? (field.placeholderKey | translate) : ''"
                        class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                      ></textarea>
                    } @else {
                      <input
                        [type]="field.inputType"
                        [(ngModel)]="credentialValues[field.key]"
                        [name]="field.key"
                        required
                        [placeholder]="field.placeholderKey ? (field.placeholderKey | translate) : ''"
                        class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                      />
                    }
                  </div>
                }
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
          <div class="dp-table-wrap">
            <table class="dp-table">
              <thead>
                <tr>
                  <th>{{ 'settings.connections.col_marketplace' | translate }}</th>
                  <th>{{ 'settings.connections.col_name' | translate }}</th>
                  <th>{{ 'settings.connections.col_status' | translate }}</th>
                  <th>{{ 'settings.connections.col_last_sync' | translate }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (conn of connections; track conn.id) {
                  <tr class="cursor-pointer" (click)="openDetail(conn)">
                    <td>
                      <dp-marketplace-badge [type]="conn.marketplaceType" />
                    </td>
                    <td class="text-[var(--text-primary)]">{{ conn.name }}</td>
                    <td>
                      <dp-status-badge [label]="conn.status | dpStatusLabel" [color]="conn.status | dpStatusColor" />
                    </td>
                    <td class="text-[var(--text-secondary)]">
                      {{ conn.lastSuccessAt | dpDateFormat }}
                    </td>
                    <td class="text-right">
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

  protected readonly rbac = inject(RbacService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly formStep = signal<FormStep>('idle');
  readonly selectedMarketplace = signal<MarketplaceType | null>(null);
  readonly selectedConfig = computed(() => {
    const mp = this.selectedMarketplace();
    return mp ? getMarketplaceConfig(mp) : null;
  });

  formName = '';
  credentialValues: Record<string, string> = {};

  readonly marketplaces = MARKETPLACE_REGISTRY;

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    staleTime: 60_000,
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
    this.credentialValues = {};
    this.formStep.set('credentials');
  }

  cancelCreate(): void {
    this.formStep.set('idle');
    this.selectedMarketplace.set(null);
    this.formName = '';
    this.credentialValues = {};
  }

  isFormValid(): boolean {
    if (!this.formName.trim()) return false;
    const config = this.selectedConfig();
    if (!config) return false;
    return config.credentialFields.every(f => !!this.credentialValues[f.key]?.trim());
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const credentials: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.credentialValues)) {
      credentials[k] = v.trim();
    }
    this.createMutation.mutate({
      marketplaceType: this.selectedMarketplace()!,
      name: this.formName.trim(),
      credentials: credentials as unknown as CreateConnectionRequest['credentials'],
    });
  }

  openDetail(conn: ConnectionSummary): void {
    this.router.navigate([
      '/workspace', this.wsStore.currentWorkspaceId(), 'settings', 'connections', conn.id,
    ]);
  }
}
