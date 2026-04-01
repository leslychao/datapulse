import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

export interface StateNode {
  id: string;
  label: string;
  status: 'completed' | 'active' | 'pending' | 'failed' | 'skipped';
}

const STATUS_STYLES: Record<StateNode['status'], { bg: string; border: string; text: string }> = {
  completed: { bg: 'var(--status-success)', border: 'var(--status-success)', text: 'white' },
  active: { bg: 'var(--accent-primary)', border: 'var(--accent-primary)', text: 'white' },
  pending: { bg: 'var(--bg-secondary)', border: 'var(--border-default)', text: 'var(--text-tertiary)' },
  failed: { bg: 'var(--status-error)', border: 'var(--status-error)', text: 'white' },
  skipped: { bg: 'var(--bg-tertiary)', border: 'var(--border-subtle)', text: 'var(--text-tertiary)' },
};

@Component({
  selector: 'dp-state-machine-visualizer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex items-center gap-1 overflow-x-auto py-2" role="list" [attr.aria-label]="ariaLabel()">
      @for (node of states(); track node.id; let last = $last) {
        <div
          class="flex shrink-0 items-center justify-center rounded-full px-3 py-1.5 text-xs font-medium transition-colors"
          [style.background-color]="nodeStyle(node.status).bg"
          [style.border-color]="nodeStyle(node.status).border"
          [style.color]="nodeStyle(node.status).text"
          style="border-width: 1.5px"
          role="listitem"
          [attr.aria-current]="node.status === 'active' ? 'step' : null"
        >
          @if (node.status === 'completed') {
            <svg class="mr-1 h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          }
          {{ node.label | translate }}
        </div>
        @if (!last) {
          <svg class="h-4 w-6 shrink-0 text-[var(--border-default)]" viewBox="0 0 24 16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 8h16m-6-5 6 5-6 5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        }
      }
    </div>
  `,
})
export class StateMachineVisualizerComponent {
  readonly states = input<StateNode[]>([]);
  readonly ariaLabel = input('Action state machine');

  protected nodeStyle(status: StateNode['status']) {
    return STATUS_STYLES[status];
  }
}
