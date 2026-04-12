import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { WorkspaceApiService } from '@core/api/workspace-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { StatusLabelPipe } from '@shared/pipes/status-label.pipe';

@Component({
  selector: 'dp-general-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    SectionCardComponent,
    EmptyStateComponent,
    SpinnerComponent,
    DateFormatPipe,
    StatusLabelPipe,
  ],
  template: `
    <div class="max-w-2xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.general.title' | translate }}</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.general.subtitle' | translate }}</p>
      </div>

      @if (workspaceQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      } @else if (workspaceQuery.isError()) {
        <dp-empty-state
          [message]="'common.load_error' | translate"
          [actionLabel]="'actions.retry' | translate"
          (action)="workspaceQuery.refetch()"
        />
      }

      @if (workspaceQuery.data(); as ws) {
        <div class="space-y-5">
          <dp-section-card [title]="'settings.general.info_title' | translate">
            <div class="space-y-4">
              @if (rbac.isAdmin()) {
                <form (ngSubmit)="saveName()" class="flex items-end gap-3">
                  <div class="flex-1">
                    <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.general.name_label' | translate }}</label>
                    <input
                      type="text"
                      [(ngModel)]="workspaceName"
                      name="workspaceName"
                      required
                      class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                    />
                  </div>
                  <button
                    type="submit"
                    [disabled]="!workspaceName.trim() || workspaceName.trim() === ws.name || updateMutation.isPending()"
                    class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    @if (updateMutation.isPending()) {
                      {{ 'settings.general.saving' | translate }}
                    } @else {
                      {{ 'actions.save' | translate }}
                    }
                  </button>
                </form>
              } @else {
                <div>
                  <span class="text-[var(--text-secondary)]">{{ 'settings.general.name_label' | translate }}</span>
                  <p class="mt-0.5 text-[var(--text-primary)]">{{ ws.name }}</p>
                </div>
              }

              <div class="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span class="text-[var(--text-secondary)]">{{ 'settings.general.slug' | translate }}</span>
                  <p class="mt-0.5 font-mono text-[var(--text-primary)]">{{ ws.slug }}</p>
                </div>
                <div>
                  <span class="text-[var(--text-secondary)]">{{ 'settings.general.created' | translate }}</span>
                  <p class="mt-0.5 text-[var(--text-primary)]">{{ ws.createdAt | dpDateFormat:'short' }}</p>
                </div>
                <div>
                  <span class="text-[var(--text-secondary)]">{{ 'settings.general.status' | translate }}</span>
                  <p class="mt-0.5 text-[var(--text-primary)]">{{ ws.status | dpStatusLabel }}</p>
                </div>
              </div>
            </div>
          </dp-section-card>

          @if (rbac.isAdmin()) {
            <dp-section-card [title]="'settings.general.organization_title' | translate">
              <div class="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span class="text-[var(--text-secondary)]">{{ 'settings.general.org_name' | translate }}</span>
                  <p class="mt-0.5 text-[var(--text-primary)]">{{ ws.tenantName }}</p>
                </div>
                <div>
                  <span class="text-[var(--text-secondary)]">{{ 'settings.general.org_slug' | translate }}</span>
                  <p class="mt-0.5 font-mono text-[var(--text-primary)]">{{ ws.tenantSlug }}</p>
                </div>
              </div>
            </dp-section-card>
          }
        </div>
      }
    </div>
  `,
})
export class GeneralPageComponent {
  private readonly workspaceApi = inject(WorkspaceApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  workspaceName = '';

  readonly workspaceQuery = injectQuery(() => ({
    queryKey: ['workspace', this.wsStore.currentWorkspaceId()],
    queryFn: async () => {
      const ws = await lastValueFrom(this.workspaceApi.getWorkspace(this.wsStore.currentWorkspaceId()!));
      this.workspaceName = ws.name;
      return ws;
    },
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly updateMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.workspaceApi.updateWorkspace(this.wsStore.currentWorkspaceId()!, this.workspaceName.trim())),
    onSuccess: (result) => {
      this.workspaceQuery.refetch();
      this.wsStore.setWorkspace(result.id, result.name);
      this.toast.success(this.translate.instant('settings.general.name_updated'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.general.name_update_error')),
  }));

  saveName(): void {
    if (!this.workspaceName.trim()) return;
    this.updateMutation.mutate(undefined);
  }
}
