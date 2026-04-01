import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-policy-scope-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <span class="inline-flex items-center gap-1 rounded-[var(--radius-sm)] bg-[var(--bg-tertiary)] px-2 py-0.5 text-[11px] font-medium text-[var(--text-secondary)]">
      <span class="text-[var(--text-tertiary)]">{{ 'scope_type.' + scopeType() | translate }}:</span>
      {{ scopeValue() }}
    </span>
  `,
})
export class PolicyScopeChipComponent {
  readonly scopeType = input.required<string>();
  readonly scopeValue = input.required<string>();
}
