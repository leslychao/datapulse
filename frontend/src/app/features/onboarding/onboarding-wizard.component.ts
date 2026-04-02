import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '@core/auth/auth.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { MinimalTopBarComponent } from '@shared/layout/minimal-top-bar.component';

import { StepTenantComponent } from './step-tenant.component';
import { StepWorkspaceComponent } from './step-workspace.component';
import { StepConnectionComponent } from './step-connection.component';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

@Component({
  selector: 'dp-onboarding-wizard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MinimalTopBarComponent,
    StepTenantComponent,
    StepWorkspaceComponent,
    StepConnectionComponent,
    TranslatePipe,
  ],
  template: `
    <div class="flex h-screen flex-col bg-[var(--bg-secondary)]">
      <dp-minimal-top-bar (logoutClick)="onLogout()" />

      <div class="flex flex-1 overflow-hidden">
        <!-- Grayed-out activity bar -->
        <aside class="flex w-12 shrink-0 flex-col items-center gap-4 border-r border-[var(--border-default)] bg-[var(--bg-secondary)] pt-4">
          @for (icon of activityBarIcons; track icon) {
            <div class="h-5 w-5 rounded-[var(--radius-sm)] bg-[var(--border-default)]"></div>
          }
        </aside>

        <!-- Main content -->
        <main class="flex flex-1 flex-col items-center overflow-y-auto px-6 pt-12">
          <!-- Stepper -->
          <div class="mb-10 flex items-center gap-0">
            @for (step of steps; track step.num; let i = $index) {
              @if (i > 0) {
                <div
                  class="h-0.5 w-12"
                  [class]="currentStep() > step.num - 1
                    ? 'bg-[var(--accent-primary)]'
                    : 'bg-[var(--border-default)]'"
                ></div>
              }

              <div class="flex items-center gap-2">
                <!-- Circle -->
                @if (currentStep() > step.num) {
                  <div class="flex h-6 w-6 items-center justify-center rounded-full bg-[var(--accent-primary)]">
                    <svg class="h-3.5 w-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                } @else if (currentStep() === step.num) {
                  <div class="flex h-6 w-6 items-center justify-center rounded-full bg-[var(--accent-primary)]">
                    <span class="text-xs font-semibold text-white">{{ step.num }}</span>
                  </div>
                } @else {
                  <div class="flex h-6 w-6 items-center justify-center rounded-full border-2 border-[var(--border-default)]">
                    <span class="text-xs text-[var(--text-secondary)]">{{ step.num }}</span>
                  </div>
                }

                <!-- Label -->
                <span
                  class="text-sm"
                  [class]="currentStep() === step.num
                    ? 'font-semibold text-[var(--text-primary)]'
                    : 'text-[var(--text-secondary)]'"
                >{{ step.labelKey | translate }}</span>
              </div>
            }
          </div>

          <!-- Step content -->
          <div class="w-full max-w-md">
            @switch (currentStep()) {
              @case (1) {
                <dp-step-tenant
                  [existingTenant]="tenantData()"
                  (created)="onTenantCreated($event)"
                />
              }
              @case (2) {
                <dp-step-workspace
                  [tenantId]="tenantId()!"
                  [tenantName]="tenantName()"
                  [existingWorkspace]="workspaceData()"
                  (created)="onWorkspaceCreated($event)"
                />
              }
              @case (3) {
                <dp-step-connection
                  [workspaceId]="workspaceId()!"
                  (completed)="onConnectionCompleted()"
                  (skipped)="onConnectionSkipped()"
                />
              }
            }
          </div>

          <!-- Navigation -->
          <div class="mt-8 flex w-full max-w-md items-center justify-between pb-8">
            <div>
              @if (currentStep() > 1) {
                <button
                  (click)="onBack()"
                  class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-4 py-2 text-sm font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                >
                  {{ 'actions.back' | translate }}
                </button>
              }
            </div>

            <div class="flex items-center gap-3">
              @if (currentStep() === 3) {
                <button
                  (click)="onSkipConnection()"
                  class="cursor-pointer px-2 py-1 text-sm text-[var(--text-secondary)] transition-colors hover:text-[var(--text-primary)]"
                >
                  {{ 'onboarding.skip' | translate }}
                </button>
              }
            </div>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class OnboardingWizardComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly workspaceStore = inject(WorkspaceContextStore);

  protected readonly currentStep = signal(1);
  protected readonly tenantId = signal<number | null>(null);
  protected readonly tenantName = signal('');
  protected readonly workspaceId = signal<number | null>(null);
  protected readonly workspaceName = signal('');

  protected readonly steps = [
    { num: 1, labelKey: 'onboarding.step_tenant' },
    { num: 2, labelKey: 'onboarding.step_workspace' },
    { num: 3, labelKey: 'onboarding.step_connection' },
  ];

  protected readonly activityBarIcons = [1, 2, 3, 4, 5];

  protected tenantData = signal<{ id: number; name: string } | null>(null);
  protected workspaceData = signal<{ id: number; name: string } | null>(null);

  protected onTenantCreated(event: { tenantId: number; tenantName: string }): void {
    this.tenantId.set(event.tenantId);
    this.tenantName.set(event.tenantName);
    this.tenantData.set({ id: event.tenantId, name: event.tenantName });
    this.currentStep.set(2);
  }

  protected onWorkspaceCreated(event: { workspaceId: number; workspaceName: string }): void {
    this.workspaceId.set(event.workspaceId);
    this.workspaceName.set(event.workspaceName);
    this.workspaceData.set({ id: event.workspaceId, name: event.workspaceName });
    this.workspaceStore.setWorkspace(event.workspaceId, event.workspaceName);
    this.currentStep.set(3);
  }

  protected onConnectionCompleted(): void {
    this.navigateToWorkspace();
  }

  protected onConnectionSkipped(): void {
    this.navigateToWorkspace();
  }

  protected onSkipConnection(): void {
    this.navigateToWorkspace();
  }

  protected onBack(): void {
    const step = this.currentStep();
    if (step > 1) {
      this.currentStep.set(step - 1);
    }
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  private navigateToWorkspace(): void {
    const wsId = this.workspaceId();
    if (wsId) {
      localStorage.setItem(LAST_WORKSPACE_KEY, String(wsId));
      this.router.navigate(['/workspace', wsId, 'grid']);
    } else {
      this.router.navigate(['/workspaces']);
    }
  }
}
