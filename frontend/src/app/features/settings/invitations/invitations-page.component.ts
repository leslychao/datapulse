import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Send, RotateCcw, X } from 'lucide-angular';

import { InvitationApiService } from '@core/api/invitation-api.service';
import { Invitation, WorkspaceRole } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-invitations-page',
  standalone: true,
  imports: [
    FormsModule,
    LucideAngularModule,
    SectionCardComponent,
    StatusBadgeComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="max-w-4xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">Приглашения</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">Пригласите участников в workspace</p>
      </div>

      <!-- Invite form -->
      <dp-section-card title="Отправить приглашение" class="mb-6">
        <form (ngSubmit)="sendInvite()" class="flex items-end gap-3">
          <div class="flex-1">
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">Email</label>
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
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">Роль</label>
            <select
              [(ngModel)]="inviteRole"
              name="inviteRole"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            >
              @for (role of assignableRoles; track role.value) {
                <option [value]="role.value">{{ role.label }}</option>
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
              Отправка...
            } @else {
              Пригласить
            }
          </button>
        </form>
        @if (createMutation.error()) {
          <p class="mt-2 text-sm text-[var(--status-error)]">Не удалось отправить приглашение. Возможно, пользователь уже приглашён.</p>
        }
      </dp-section-card>

      <!-- Invitations list -->
      @if (invitationsQuery.isPending()) {
        <div class="flex items-center gap-2 py-8 text-sm text-[var(--text-secondary)]">
          <span class="dp-spinner inline-block h-4 w-4 rounded-full border-2 border-[var(--border-default)] border-t-[var(--accent-primary)]"></span>
          Загрузка...
        </div>
      }

      @if (invitationsQuery.data(); as invitations) {
        @if (invitations.length === 0) {
          <div class="rounded-[var(--radius-md)] border border-dashed border-[var(--border-default)] bg-[var(--bg-secondary)] py-12 text-center">
            <p class="text-sm text-[var(--text-secondary)]">Нет приглашений</p>
          </div>
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Email</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Роль</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Статус</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Создано</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Истекает</th>
                  <th class="px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (inv of invitations; track inv.id) {
                  <tr class="border-b border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-secondary)]">
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ inv.email }}</td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ roleLabel(inv.role) }}</td>
                    <td class="px-4 py-2.5">
                      <dp-status-badge [label]="invStatusLabel(inv.status)" [color]="invStatusColor(inv.status)" />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ formatDate(inv.createdAt) }}</td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">{{ formatDate(inv.expiresAt) }}</td>
                    <td class="px-4 py-2.5 text-right">
                      @if (inv.status === 'PENDING') {
                        <div class="flex items-center justify-end gap-2">
                          <button
                            (click)="resend(inv)"
                            [disabled]="resendMutation.isPending()"
                            class="flex cursor-pointer items-center gap-1 text-sm text-[var(--accent-primary)] transition-colors hover:underline disabled:opacity-50"
                            title="Отправить повторно"
                          >
                            <lucide-icon [img]="ResendIcon" [size]="13" />
                            Повторить
                          </button>
                          <button
                            (click)="confirmCancel(inv)"
                            class="flex cursor-pointer items-center gap-1 text-sm text-[var(--status-error)] transition-colors hover:underline"
                            title="Отменить"
                          >
                            <lucide-icon [img]="CancelIcon" [size]="13" />
                            Отмена
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
        title="Отменить приглашение"
        [message]="'Отменить приглашение для ' + (invToCancel()?.email || '') + '?'"
        confirmLabel="Отменить приглашение"
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

  inviteEmail = '';
  inviteRole: WorkspaceRole = 'VIEWER';

  readonly showCancelModal = signal(false);
  readonly invToCancel = signal<Invitation | null>(null);

  readonly assignableRoles = [
    { value: 'ADMIN' as WorkspaceRole, label: 'Администратор' },
    { value: 'PRICING_MANAGER' as WorkspaceRole, label: 'Менеджер цен' },
    { value: 'OPERATOR' as WorkspaceRole, label: 'Оператор' },
    { value: 'ANALYST' as WorkspaceRole, label: 'Аналитик' },
    { value: 'VIEWER' as WorkspaceRole, label: 'Наблюдатель' },
  ];

  private get wsId(): number {
    return this.wsStore.currentWorkspaceId()!;
  }

  readonly invitationsQuery = injectQuery(() => ({
    queryKey: ['invitations', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.invitationApi.listInvitations(this.wsId)),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly createMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.invitationApi.createInvitation(this.wsId, {
        email: this.inviteEmail.trim(),
        role: this.inviteRole,
      })),
    onSuccess: () => {
      this.invitationsQuery.refetch();
      this.inviteEmail = '';
      this.toast.success('Приглашение отправлено');
    },
  }));

  readonly resendMutation = injectMutation(() => ({
    mutationFn: (invitationId: number) =>
      lastValueFrom(this.invitationApi.resendInvitation(this.wsId, invitationId)),
    onSuccess: () => {
      this.invitationsQuery.refetch();
      this.toast.success('Приглашение отправлено повторно');
    },
    onError: () => this.toast.error('Не удалось отправить повторно'),
  }));

  readonly cancelMutation = injectMutation(() => ({
    mutationFn: (invitationId: number) =>
      lastValueFrom(this.invitationApi.cancelInvitation(this.wsId, invitationId)),
    onSuccess: () => {
      this.invitationsQuery.refetch();
      this.showCancelModal.set(false);
      this.toast.success('Приглашение отменено');
    },
    onError: () => this.toast.error('Не удалось отменить приглашение'),
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

  roleLabel(role: WorkspaceRole): string {
    switch (role) {
      case 'OWNER': return 'Владелец';
      case 'ADMIN': return 'Администратор';
      case 'PRICING_MANAGER': return 'Менеджер цен';
      case 'OPERATOR': return 'Оператор';
      case 'ANALYST': return 'Аналитик';
      case 'VIEWER': return 'Наблюдатель';
    }
  }

  invStatusLabel(status: string): string {
    switch (status) {
      case 'PENDING': return 'Ожидает';
      case 'ACCEPTED': return 'Принято';
      case 'CANCELLED': return 'Отменено';
      case 'EXPIRED': return 'Истекло';
      default: return status;
    }
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

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('ru-RU', {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  }
}
