import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

export interface ExplanationSection {
  title: string;
  entries: { label: string; value: string; highlight?: boolean }[];
}

@Component({
  selector: 'dp-explanation-block',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex flex-col gap-3 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-4">
      @if (title()) {
        <h4 class="text-sm font-semibold text-[var(--text-primary)]">{{ title() | translate }}</h4>
      }
      @for (section of sections(); track section.title) {
        <div class="flex flex-col gap-1">
          <span class="text-xs font-medium text-[var(--text-secondary)]">{{ section.title | translate }}</span>
          @for (entry of section.entries; track entry.label) {
            <div class="flex items-baseline justify-between gap-4 text-sm">
              <span class="text-[var(--text-secondary)]">{{ entry.label | translate }}</span>
              <span
                class="font-mono"
                [class]="entry.highlight ? 'font-semibold text-[var(--accent-primary)]' : 'text-[var(--text-primary)]'"
              >{{ entry.value }}</span>
            </div>
          }
        </div>
      }
      @if (summary()) {
        <div class="mt-1 border-t border-[var(--border-subtle)] pt-2 text-sm text-[var(--text-secondary)]">
          {{ summary() }}
        </div>
      }
    </div>
  `,
})
export class ExplanationBlockComponent {
  readonly title = input('');
  readonly sections = input<ExplanationSection[]>([]);
  readonly summary = input('');
}
