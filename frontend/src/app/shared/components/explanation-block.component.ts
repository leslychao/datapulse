import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

export interface ExplanationSection {
  label: string;
  content: string;
}

/**
 * Parses `explanation_summary`-style text into sections. Headers are lines that
 * match `^\s*\[([^\]]+)\]\s*$` (a single bracketed label on a line).
 */
function parseExplanationSections(raw: string): ExplanationSection[] {
  const lines = raw.split('\n');
  const headerRe = /^\s*\[([^\]]+)\]\s*$/;
  const sections: ExplanationSection[] = [];
  let currentLabel: string | null = null;
  const contentLines: string[] = [];
  const pendingPreamble: string[] = [];

  const flush = (): void => {
    if (currentLabel === null) {
      return;
    }
    sections.push({
      label: currentLabel,
      content: contentLines.join('\n').replace(/\s+$/u, ''),
    });
  };

  for (const line of lines) {
    const hm = line.match(headerRe);
    if (hm) {
      flush();
      currentLabel = hm[1].trim();
      contentLines.length = 0;
      if (sections.length === 0 && pendingPreamble.length > 0) {
        contentLines.push(...pendingPreamble);
        pendingPreamble.length = 0;
      }
    } else if (currentLabel === null) {
      pendingPreamble.push(line);
    } else {
      contentLines.push(line);
    }
  }
  flush();

  return sections.length > 0 ? sections : [];
}

@Component({
  selector: 'dp-explanation-block',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-4"
    >
      @if (sections().length > 0) {
        @for (section of sections(); track section.label; let last = $last) {
          <div
            [class]="last ? 'py-3' : 'border-b border-[var(--border-subtle)] py-3'"
            [class.pt-0]="$first"
          >
            <h4
              class="mb-1 text-[length:var(--text-sm)] font-semibold uppercase text-[var(--text-secondary)]"
            >
              {{ section.label }}
            </h4>
            <div class="whitespace-pre-line text-sm text-[var(--text-primary)]">
              {{ section.content }}
            </div>
          </div>
        }
      } @else {
        <div class="whitespace-pre-line text-sm text-[var(--text-primary)]">
          {{ text() }}
        </div>
      }
    </div>
  `,
})
export class ExplanationBlockComponent {
  readonly text = input.required<string>();

  readonly sections = computed(() => parseExplanationSections(this.text()));
}
