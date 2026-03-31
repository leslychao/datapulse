import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { MemberApiService } from '@core/api/member-api.service';
import { Member, WorkspaceRole } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-team-page',
  standalone: true,
  imports: [FormsModule, SectionCardComponent, ConfirmationModalComponent],
  template: `
    <div class="max-w-4xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">Команда</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">Участники workspace и их роли</p>
      </div>

      @if (membersQuery.isPending()) {
        <div class="flex items-center gap-2 py-8 text-sm text-[var(--text-secondary)]">
          <span class="dp-spinner inline-block h-4 w-4 rounded-full border-2 border-[var(--border-default)] border-t-[var(--accent-primary)]"></span>
          Загрузка...
        </div>
      }

      @if (membersQuery.data(); as members) {
        @if (members.length === 0) {
          <div class="rounded-[var(--radius-md)] border border-dashed border-[var(--border-default)] bg-[var(--bg-secondary)] py-12 text-center">
            <p class="text-sm text-[var(--text-secondary)]">Нет участников</p>
          </div>
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Имя</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Email</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Роль</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Дата добавления</th>
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
                        <span class="text-sm font-medium text-[var(--text-primary)]">{{ roleLabel(m.role) }}</span>
                      } @else {
                        <select
                          [ngModel]="m.role"
                          (ngModelChange)="changeRole(m, $event)"
                          [disabled]="roleChangeMutation.isPending()"
                          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 py-1 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)] disabled:opacity-50"
                        >
                          @for (role of assignableRoles; track role) {
                            <option [value]="role">{{ roleLabel(role) }}</option>
                          }
                        </select>
                      }
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                      {{ formatDate(m.createdAt) }}
                    </td>
                    <td class="px-4 py-2.5 text-right">
                      @if (m.role !== 'OWNER') {
                        <button
                          (click)="confirmRemove(m)"
                          class="cursor-pointer text-sm text-[var(--status-error)] transition-colors hover:underline"
                        >
                          Удалить
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
        title="Удалить участника"
        [message]="'Вы уверены, что хотите удалить ' + (memberToRemove()?.name || '') + ' из workspace?'"
        confirmLabel="Удалить"
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

  readonly showRemoveModal = signal(false);
  readonly memberToRemove = signal<Member | null>(null);

  readonly assignableRoles: WorkspaceRole[] = ['ADMIN', 'PRICING_MANAGER', 'OPERATOR', 'ANALYST', 'VIEWER'];

  private get wsId(): number {
    return this.wsStore.currentWorkspaceId()!;
  }

  readonly membersQuery = injectQuery(() => ({
    queryKey: ['members', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.memberApi.listMembers(this.wsId)),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly roleChangeMutation = injectMutation(() => ({
    mutationFn: (vars: { userId: number; role: WorkspaceRole }) =>
      lastValueFrom(this.memberApi.changeRole(this.wsId, vars.userId, { role: vars.role })),
    onSuccess: () => {
      this.membersQuery.refetch();
      this.toast.success('Роль обновлена');
    },
    onError: () => this.toast.error('Не удалось изменить роль'),
  }));

  readonly removeMutation = injectMutation(() => ({
    mutationFn: (userId: number) => lastValueFrom(this.memberApi.removeMember(this.wsId, userId)),
    onSuccess: () => {
      this.membersQuery.refetch();
      this.showRemoveModal.set(false);
      this.toast.success('Участник удалён');
    },
    onError: () => this.toast.error('Не удалось удалить участника'),
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

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('ru-RU', {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  }
}
