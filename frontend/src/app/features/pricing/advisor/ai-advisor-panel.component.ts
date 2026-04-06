import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Bot, ChevronDown, ChevronUp, AlertCircle } from 'lucide-angular';

import { PricingAiApiService } from '@core/api/pricing-ai-api.service';
import { AdvisorResponse } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

@Component({
  selector: 'dp-ai-advisor-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule],
  template: `
    <div class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                bg-[var(--bg-secondary)]">
      <button
        type="button"
        class="flex w-full items-center gap-2 px-4 py-3 text-left text-[length:var(--text-sm)]
               font-medium text-[var(--text-primary)] transition-colors
               hover:bg-[var(--bg-tertiary)]"
        (click)="toggle()"
      >
        <lucide-icon [img]="BotIcon" size="16"
                     class="text-[var(--accent-primary)]" />
        {{ 'pricing.advisor.title' | translate }}
        <lucide-icon
          [img]="expanded() ? ChevronUpIcon : ChevronDownIcon"
          size="14"
          class="ml-auto text-[var(--text-tertiary)]"
        />
      </button>

      @if (expanded()) {
        <div class="border-t border-[var(--border-subtle)] px-4 py-3">
          @if (loading()) {
            <div class="flex items-center gap-2 text-[length:var(--text-sm)]
                        text-[var(--text-secondary)]">
              <div class="h-4 w-4 animate-spin rounded-full border-2
                          border-[var(--accent-primary)] border-t-transparent"></div>
              {{ 'pricing.advisor.loading' | translate }}
            </div>
          } @else if (error()) {
            <div class="flex items-center gap-2 text-[length:var(--text-sm)]
                        text-[var(--status-error)]">
              <lucide-icon [img]="AlertCircleIcon" size="14" />
              {{ 'pricing.advisor.unavailable' | translate }}
            </div>
          } @else if (advice()) {
            <div class="space-y-2">
              <p class="text-[length:var(--text-sm)] text-[var(--text-primary)] leading-relaxed">
                {{ advice()!.advice }}
              </p>
              @if (advice()!.generatedAt) {
                <p class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'pricing.advisor.generated_at' | translate }}:
                  {{ advice()!.generatedAt }}
                </p>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class AiAdvisorPanelComponent {
  readonly offerId = input.required<number>();

  private readonly aiApi = inject(PricingAiApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly expanded = signal(false);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly advice = signal<AdvisorResponse | null>(null);

  readonly BotIcon = Bot;
  readonly ChevronDownIcon = ChevronDown;
  readonly ChevronUpIcon = ChevronUp;
  readonly AlertCircleIcon = AlertCircle;

  private loaded = false;

  toggle(): void {
    this.expanded.update((v) => !v);
    if (this.expanded() && !this.loaded) {
      this.loadAdvice();
    }
  }

  private async loadAdvice(): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      const wsId = this.wsStore.currentWorkspaceId();
      if (!wsId) return;

      const result = await lastValueFrom(
        this.aiApi.generateAdvice(wsId, this.offerId()),
      );

      if (result.error) {
        this.error.set(true);
      } else {
        this.advice.set(result);
      }
      this.loaded = true;
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
