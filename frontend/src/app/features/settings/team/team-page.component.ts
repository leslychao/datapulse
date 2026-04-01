import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { MemberApiService } from '@core/api/member-api.service';
import { Member, WorkspaceRole } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { RoleLabelPipe } from '@shared/pipes/role-label.pipe';

@Component({
  selector: 'dp-team-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    ConfirmationModalComponent,
    SpinnerComponent,
    EmptyStateComponent,
    DateFormatPipe,
    RoleLabelPipe,
  ],
  template: `
    <div class="max-w-4xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.team.title' | translate }}</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.team.subtitle' | translate }}</p>
      </div>

      @if (membersQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (membersQuery.data(); as members) {
        @if (members.length === 0) {
          <dp-empty-state [message]="'settings.team.empty' | translate" />
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.team.col_name' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.team.col_email' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.team.col_role' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.team.col_joined' | translate }}</th>
                  <th class="px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (m of members; track m.userId) {
                  <tr class="border-b border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-secondary)]">
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ m.name }}</td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ m.email }}</td>
                    <td class="px-4 py-2.5">
                      @if (m.role === 'OWNER') {
                        <span class="text-sm font-medium text-[var(--text-primary)]">{{ m.role | dpRoleLabel }}</span>
                      } @else {
                        <select
                          [ngModel]="m.role"
                          (ngModelChange)="changeRole(m, $event)"
                          [disabled]="roleChangeMutation.isPending()"
                          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 py-1 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)] disabled:opacity-50"
                        >
                          @for (role of assignableRoles; track role) {
                            <option [value]="role">{{ role | dpRoleLabel }}</option>
                          }
                        </select>
                      }
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                      {{ m.createdAt | dpDateFormat:'short' }}
                    </td>
                    <td class="px-4 py-2.5 text-right">
                      @if (m.role !== 'OWNER') {
                        <button
                          (click)="confirmRemove(m)"
                          class="cursor-pointer text-sm text-[var(--status-error)] transition-colors hover:underline"
                        >
                          {{ 'actions.delete' | translate }}
                        </button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }

      <dp-confirmation-modal
        [open]="showRemoveModal()"
        [title]="'settings.team.remove_title' | translate"
        [message]="translate.instant('settings.team.remove_message', { name: memberToRemove()?.name || '' })"
        [confirmLabel]="'actions.delete' | translate"
        [danger]="true"
        (confirmed)="doRemove()"
        (cancelled)="showRemoveModal.set(false)"
      />
    </div>
  `,
})
export class TeamPageComponent {
  private readonly memberApi = inject(MemberApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  protected readonly translate = inject(TranslateService);

  readonly showRemoveModal = signal(false);
  readonly memberToRemove = signal<Member | null>(null);

  readonly assignableRoles: WorkspaceRole[] = ['ADMIN', 'PRICING_MANAGER', 'OPERATOR', 'ANALYST', 'VIEWER'];

  readonly membersQuery = injectQuery(() => ({
    queryKey: ['members', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.memberApi.listMembers(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly roleChangeMutation = injectMutation(() => ({
    mutationFn: (vars: { userId: number; role: WorkspaceRole }) =>
      lastValueFrom(this.memberApi.changeRole(this.wsStore.currentWorkspaceId()!, vars.userId, { role: vars.role })),
    onSuccess: () => {
      this.membersQuery.refetch();
      this.toast.success(this.translate.instant('settings.team.role_updated'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.team.role_update_error')),
  }));

  readonly removeMutation = injectMutation(() => ({
    mutationFn: (userId: number) =>
      lastValueFrom(this.memberApi.removeMember(this.wsStore.currentWorkspaceId()!, userId)),
    onSuccess: () => {
      this.membersQuery.refetch();
      this.showRemoveModal.set(false);
      this.toast.success(this.translate.instant('settings.team.member_removed'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.team.member_remove_error')),
  }));

  changeRole(member: Member, newRole: WorkspaceRole): void {
    this.roleChangeMutation.mutate({ userId: member.userId, role: newRole });
  }

  confirmRemove(member: Member): void {
    this.memberToRemove.set(member);
    this.showRemoveModal.set(true);
  }

  doRemove(): void {
    const member = this.memberToRemove();
    if (!member) return;
    this.removeMutation.mutate(member.userId);
  }
}
