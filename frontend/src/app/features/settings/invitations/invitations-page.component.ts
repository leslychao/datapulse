import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Send, RotateCcw, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { InvitationApiService } from '@core/api/invitation-api.service';
import { Invitation, WorkspaceRole } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { RoleLabelPipe } from '@shared/pipes/role-label.pipe';

@Component({
  selector: 'dp-invitations-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    LucideAngularModule,
    TranslatePipe,
    SectionCardComponent,
    StatusBadgeComponent,
    ConfirmationModalComponent,
    SpinnerComponent,
    EmptyStateComponent,
    DateFormatPipe,
    RoleLabelPipe,
  ],
  template: `
    <div class="max-w-4xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.invitations.title' | translate }}</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.invitations.subtitle' | translate }}</p>
      </div>

      <dp-section-card [title]="'settings.invitations.send_title' | translate" class="mb-6">
        <form (ngSubmit)="sendInvite()" class="flex items-end gap-3">
          <div class="flex-1">
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.invitations.email_label' | translate }}</label>
            <input
              type="email"
              [(ngModel)]="inviteEmail"
              name="inviteEmail"
              required
              placeholder="user{'@'}example.com"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            />
          </div>
          <div class="w-48">
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.invitations.role_label' | translate }}</label>
            <select
              [(ngModel)]="inviteRole"
              name="inviteRole"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            >
              @for (role of assignableRoles; track role.value) {
                <option [value]="role.value">{{ role.value | dpRoleLabel }}</option>
              }
            </select>
          </div>
          <button
            type="submit"
            [disabled]="!inviteEmail.trim() || createMutation.isPending()"
            class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            <lucide-icon [img]="SendIcon" [size]="14" />
            @if (createMutation.isPending()) {
              {{ 'settings.invitations.sending' | translate }}
            } @else {
              {{ 'settings.invitations.invite' | translate }}
            }
          </button>
        </form>
        @if (createMutation.error()) {
          <p class="mt-2 text-sm text-[var(--status-error)]">{{ 'settings.invitations.create_error_message' | translate }}</p>
        }
      </dp-section-card>

      @if (invitationsQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (invitationsQuery.data(); as invitations) {
        @if (invitations.length === 0) {
          <dp-empty-state [message]="'settings.invitations.empty' | translate" />
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.invitations.col_email' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.invitations.col_role' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.invitations.col_status' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.invitations.col_created' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.invitations.col_expires' | translate }}</th>
                  <th class="px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (inv of invitations; track inv.id) {
                  <tr class="border-b border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-secondary)]">
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ inv.email }}</td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ inv.role | dpRoleLabel }}</td>
                    <td class="px-4 py-2.5">
                      <dp-status-badge [label]="invStatusLabel(inv.status)" [color]="invStatusColor(inv.status)" />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ inv.createdAt | dpDateFormat:'short' }}</td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ inv.expiresAt | dpDateFormat:'short' }}</td>
                    <td class="px-4 py-2.5 text-right">
                      @if (inv.status === 'PENDING') {
                        <div class="flex items-center justify-end gap-2">
                          <button
                            (click)="resend(inv)"
                            [disabled]="resendMutation.isPending()"
                            class="flex cursor-pointer items-center gap-1 text-sm text-[var(--accent-primary)] transition-colors hover:underline disabled:opacity-50"
                            [title]="'settings.invitations.resend_tooltip' | translate"
                          >
                            <lucide-icon [img]="ResendIcon" [size]="13" />
                            {{ 'settings.invitations.resend' | translate }}
                          </button>
                          <button
                            (click)="confirmCancel(inv)"
                            class="flex cursor-pointer items-center gap-1 text-sm text-[var(--status-error)] transition-colors hover:underline"
                            [title]="'actions.cancel' | translate"
                          >
                            <lucide-icon [img]="CancelIcon" [size]="13" />
                            {{ 'actions.cancel' | translate }}
                          </button>
                        </div>
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
        [open]="showCancelModal()"
        [title]="'settings.invitations.cancel_title' | translate"
        [message]="translate.instant('settings.invitations.cancel_message', { email: invToCancel()?.email || '' })"
        [confirmLabel]="'settings.invitations.cancel_confirm' | translate"
        [danger]="true"
        (confirmed)="doCancel()"
        (cancelled)="showCancelModal.set(false)"
      />
    </div>
  `,
})
export class InvitationsPageComponent {
  protected readonly SendIcon = Send;
  protected readonly ResendIcon = RotateCcw;
  protected readonly CancelIcon = X;

  private readonly invitationApi = inject(InvitationApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  protected readonly translate = inject(TranslateService);

  inviteEmail = '';
  inviteRole: WorkspaceRole = 'VIEWER';

  readonly showCancelModal = signal(false);
  readonly invToCancel = signal<Invitation | null>(null);

  readonly assignableRoles = [
    { value: 'ADMIN' as WorkspaceRole },
    { value: 'PRICING_MANAGER' as WorkspaceRole },
    { value: 'OPERATOR' as WorkspaceRole },
    { value: 'ANALYST' as WorkspaceRole },
    { value: 'VIEWER' as WorkspaceRole },
  ];

  readonly invitationsQuery = injectQuery(() => ({
    queryKey: ['invitations', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.invitationApi.listInvitations(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly createMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.invitationApi.createInvitation(this.wsStore.currentWorkspaceId()!, {
        email: this.inviteEmail.trim(),
        role: this.inviteRole,
      })),
    onSuccess: () => {
      this.invitationsQuery.refetch();
      this.inviteEmail = '';
      this.toast.success(this.translate.instant('settings.invitations.sent'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.invitations.send_error')),
  }));

  readonly resendMutation = injectMutation(() => ({
    mutationFn: (invitationId: number) =>
      lastValueFrom(this.invitationApi.resendInvitation(this.wsStore.currentWorkspaceId()!, invitationId)),
    onSuccess: () => {
      this.invitationsQuery.refetch();
      this.toast.success(this.translate.instant('settings.invitations.resent'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.invitations.resend_error')),
  }));

  readonly cancelMutation = injectMutation(() => ({
    mutationFn: (invitationId: number) =>
      lastValueFrom(this.invitationApi.cancelInvitation(this.wsStore.currentWorkspaceId()!, invitationId)),
    onSuccess: () => {
      this.invitationsQuery.refetch();
      this.showCancelModal.set(false);
      this.toast.success(this.translate.instant('settings.invitations.cancelled'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.invitations.cancel_error')),
  }));

  sendInvite(): void {
    if (!this.inviteEmail.trim()) return;
    this.createMutation.mutate(undefined);
  }

  resend(inv: Invitation): void {
    this.resendMutation.mutate(inv.id);
  }

  confirmCancel(inv: Invitation): void {
    this.invToCancel.set(inv);
    this.showCancelModal.set(true);
  }

  doCancel(): void {
    const inv = this.invToCancel();
    if (!inv) return;
    this.cancelMutation.mutate(inv.id);
  }

  invStatusLabel(status: string): string {
    return this.translate.instant(`settings.invitations.status_${status}`);
  }

  invStatusColor(status: string): 'success' | 'error' | 'warning' | 'info' | 'neutral' {
    switch (status) {
      case 'PENDING': return 'info';
      case 'ACCEPTED': return 'success';
      case 'CANCELLED': return 'neutral';
      case 'EXPIRED': return 'warning';
      default: return 'neutral';
    }
  }
}
