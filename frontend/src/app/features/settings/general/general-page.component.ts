import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { WorkspaceApiService } from '@core/api/workspace-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SectionCardComponent } from '@shared/components/section-card.component';

@Component({
  selector: 'dp-general-page',
  standalone: true,
  imports: [FormsModule, SectionCardComponent],
  template: `
    <div class="max-w-2xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">Общие настройки</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">Основная информация о workspace</p>
      </div>

      @if (workspaceQuery.isPending()) {
        <div class="flex items-center gap-2 py-8 text-sm text-[var(--text-secondary)]">
          <span class="dp-spinner inline-block h-4 w-4 rounded-full border-2 border-[var(--border-default)] border-t-[var(--accent-primary)]"></span>
          Загрузка...
        </div>
      }

      @if (workspaceQuery.data(); as ws) {
        <div class="space-y-5">
        <dp-section-card title="Информация">
          <div class="space-y-4">
            <div class="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span class="text-[var(--text-secondary)]">Slug</span>
                <p class="mt-0.5 font-mono text-[var(--text-primary)]">{{ ws.slug }}</p>
              </div>
              <div>
                <span class="text-[var(--text-secondary)]">Организация</span>
                <p class="mt-0.5 text-[var(--text-primary)]">{{ ws.tenantName }}</p>
              </div>
              <div>
                <span class="text-[var(--text-secondary)]">Создан</span>
                <p class="mt-0.5 text-[var(--text-primary)]">{{ formatDate(ws.createdAt) }}</p>
              </div>
              <div>
                <span class="text-[var(--text-secondary)]">Статус</span>
                <p class="mt-0.5 text-[var(--text-primary)]">{{ ws.status }}</p>
              </div>
            </div>
          </div>
        </dp-section-card>

        <dp-section-card title="Название workspace">
          <form (ngSubmit)="saveName()" class="flex items-end gap-3">
            <div class="flex-1">
              <label class="mb-1 block text-sm text-[var(--text-secondary)]">Название</label>
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
                Сохранение...
              } @else {
                Сохранить
              }
            </button>
          </form>
        </dp-section-card>
        </div>
      }
    </div>
  `,
})
export class GeneralPageComponent {
  private readonly workspaceApi = inject(WorkspaceApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);

  workspaceName = '';

  private get wsId(): number {
    return this.wsStore.currentWorkspaceId()!;
  }

  readonly workspaceQuery = injectQuery(() => ({
    queryKey: ['workspace', this.wsStore.currentWorkspaceId()],
    queryFn: async () => {
      const ws = await lastValueFrom(this.workspaceApi.getWorkspace(this.wsId));
      this.workspaceName = ws.name;
      return ws;
    },
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly updateMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.workspaceApi.updateWorkspace(this.wsId, this.workspaceName.trim())),
    onSuccess: (result) => {
      this.workspaceQuery.refetch();
      this.wsStore.setWorkspace(result.id, result.name);
      this.toast.success('Название обновлено');
    },
    onError: () => this.toast.error('Не удалось обновить название'),
  }));

  saveName(): void {
    if (!this.workspaceName.trim()) return;
    this.updateMutation.mutate(undefined);
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('ru-RU', {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  }
}
