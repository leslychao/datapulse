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
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { WorkspaceApiService } from '@core/api/workspace-api.service';

@Component({
  selector: 'dp-step-tenant',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div class="flex flex-col gap-6">
      <div>
        <h2 class="text-lg font-semibold text-[var(--text-primary)]">{{ 'onboarding.tenant.title' | translate }}</h2>
        <p class="mt-1 text-sm text-[var(--text-secondary)]">
          {{ 'onboarding.tenant.description' | translate }}
        </p>
      </div>

      @if (existingTenant) {
        <div class="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <span class="text-sm text-[var(--text-secondary)]">{{ 'onboarding.tenant.label' | translate }}</span>
          <span class="text-base font-medium text-[var(--text-primary)]">{{ existingTenant.name }}</span>
          <span class="text-xs text-[var(--status-success)]">{{ 'onboarding.tenant.created' | translate }}</span>
        </div>

        <button
          (click)="created.emit({ tenantId: existingTenant!.id, tenantName: existingTenant!.name })"
          class="cursor-pointer self-end rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          {{ 'actions.next' | translate }}
        </button>
      } @else {
        <form (submit)="$event.preventDefault(); onSubmit()" class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <label for="tenantName" class="text-sm font-medium text-[var(--text-primary)]">
              {{ 'onboarding.tenant.name_label' | translate }}
            </label>
            <input
              id="tenantName"
              type="text"
              [formControl]="nameControl"
              [placeholder]="'onboarding.tenant.name_placeholder' | translate"
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
            />
            @if (nameControl.touched && nameControl.hasError('required')) {
              <span class="text-xs text-[var(--status-error)]">{{ 'form.required' | translate }}</span>
            }
            @if (nameControl.touched && nameControl.hasError('minlength')) {
              <span class="text-xs text-[var(--status-error)]">{{ 'form.min_length_3' | translate }}</span>
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
              {{ 'onboarding.tenant.creating' | translate }}
            } @else {
              {{ 'actions.create' | translate }}
            }
          </button>
        </form>
      }
    </div>
  `,
})
export class StepTenantComponent {
  private readonly workspaceApi = inject(WorkspaceApiService);
  private readonly translate = inject(TranslateService);

  @Input() existingTenant: { id: number; name: string } | null = null;
  @Output() created = new EventEmitter<{ tenantId: number; tenantName: string }>();

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

    this.workspaceApi.createTenant({ name }).subscribe({
      next: (tenant) => {
        this.submitting.set(false);
        this.created.emit({ tenantId: tenant.id, tenantName: tenant.name });
      },
      error: () => {
        this.submitting.set(false);
        this.serverError.set(this.translate.instant('onboarding.tenant.create_error'));
      },
    });
  }
}
