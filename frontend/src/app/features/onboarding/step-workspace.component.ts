import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  Output,
  signal,
} from '@angular/core';
import { ReactiveFormsModule, FormControl, Validators } from '@angular/forms';

import { WorkspaceApiService } from '@core/api/workspace-api.service';

@Component({
  selector: 'dp-step-workspace',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <div class="flex flex-col gap-6">
      <div>
        <h2 class="text-lg font-semibold text-[var(--text-primary)]">Создайте рабочее пространство</h2>
        <p class="mt-1 text-sm text-[var(--text-secondary)]">
          Рабочее пространство — это место, где вы подключаете маркетплейсы и управляете ценами.
        </p>
      </div>

      <div class="rounded-[var(--radius-md)] bg-[var(--bg-tertiary)] px-3 py-2">
        <span class="text-xs text-[var(--text-secondary)]">Организация:</span>
        <span class="ml-1 text-sm font-medium text-[var(--text-primary)]">{{ tenantName }}</span>
      </div>

      @if (existingWorkspace) {
        <div class="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <span class="text-sm text-[var(--text-secondary)]">Рабочее пространство</span>
          <span class="text-base font-medium text-[var(--text-primary)]">{{ existingWorkspace.name }}</span>
          <span class="text-xs text-[var(--status-success)]">Пространство создано</span>
        </div>

        <button
          (click)="created.emit({ workspaceId: existingWorkspace!.id, workspaceName: existingWorkspace!.name })"
          class="cursor-pointer self-end rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          Далее
        </button>
      } @else {
        <form (submit)="$event.preventDefault(); onSubmit()" class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <label for="workspaceName" class="text-sm font-medium text-[var(--text-primary)]">
              Название пространства
            </label>
            <input
              id="workspaceName"
              type="text"
              [formControl]="nameControl"
              placeholder="Например, «Основной магазин» или «Бренд X»"
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
            />
            @if (nameControl.touched && nameControl.hasError('required')) {
              <span class="text-xs text-[var(--status-error)]">Название обязательно</span>
            }
            @if (nameControl.touched && nameControl.hasError('minlength')) {
              <span class="text-xs text-[var(--status-error)]">Минимум 3 символа</span>
            }
          </div>

          @if (serverError()) {
            <span class="text-xs text-[var(--status-error)]">{{ serverError() }}</span>
          }

          <button
            type="submit"
            [disabled]="nameControl.invalid || submitting()"
            class="cursor-pointer self-end rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            @if (submitting()) {
              Создание...
            } @else {
              Создать
            }
          </button>
        </form>
      }
    </div>
  `,
})
export class StepWorkspaceComponent {
  private readonly workspaceApi = inject(WorkspaceApiService);

  @Input() tenantId!: number;
  @Input() tenantName!: string;
  @Input() existingWorkspace: { id: number; name: string } | null = null;
  @Output() created = new EventEmitter<{ workspaceId: number; workspaceName: string }>();

  protected readonly nameControl = new FormControl('', [
    Validators.required,
    Validators.minLength(3),
    Validators.maxLength(255),
  ]);

  protected readonly submitting = signal(false);
  protected readonly serverError = signal('');

  protected onSubmit(): void {
    if (this.nameControl.invalid) return;

    this.submitting.set(true);
    this.serverError.set('');

    const name = this.nameControl.value!.trim();

    this.workspaceApi.createWorkspace(this.tenantId, { name }).subscribe({
      next: (workspace) => {
        this.submitting.set(false);
        this.created.emit({ workspaceId: workspace.id, workspaceName: workspace.name });
      },
      error: () => {
        this.submitting.set(false);
        this.serverError.set('Не удалось создать пространство. Попробуйте ещё раз.');
      },
    });
  }
}
